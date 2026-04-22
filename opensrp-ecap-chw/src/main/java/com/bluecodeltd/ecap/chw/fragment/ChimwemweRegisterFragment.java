package com.bluecodeltd.ecap.chw.fragment;

import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bluecodeltd.ecap.chw.util.Threading;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bluecodeltd.ecap.chw.R;
import com.bluecodeltd.ecap.chw.activity.HotspotGroupDetailActivity;
import com.bluecodeltd.ecap.chw.contract.ChimwemweRegisterFragmentContract;
import com.bluecodeltd.ecap.chw.presenter.ChimwemweRegisterPresenter;
import com.bluecodeltd.ecap.chw.provider.ChimwemweRegisterProvider;
import com.bluecodeltd.ecap.chw.util.Constants;

import org.smartregister.chw.core.custom_views.NavigationMenu;
import org.smartregister.commonregistry.CommonPersonObjectClient;
import org.smartregister.cursoradapter.RecyclerViewPaginatedAdapter;
import org.smartregister.util.Utils;

import java.util.HashMap;

import timber.log.Timber;

public class ChimwemweRegisterFragment extends BaseSafeRegisterFragment
        implements ChimwemweRegisterFragmentContract.View {

    private static final String TAG   = "ChimwemweRegFrag";
    private static final String TABLE = "ec_chimwemwe_group";
    private static final long   SEARCH_DEBOUNCE_MS = 350L;

    private final Handler  searchHandler = new Handler(Looper.getMainLooper());
    private       String   pendingSearchText = "";

    private final Runnable searchRunnable = () -> applySearch(pendingSearchText);

    private final TextWatcher debouncedSearchWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            pendingSearchText = s == null ? "" : s.toString();
            searchHandler.removeCallbacks(searchRunnable);
            searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
        }
    };

    @Override
    protected void initializePresenter() {
        ChimwemweRegisterPresenter p = new ChimwemweRegisterPresenter();
        p.initView(this);
        this.presenter = p;
    }

    @Override
    public void setupViews(View view) {
        try {
            super.setupViews(view);

            Toolbar toolbar = view.findViewById(org.smartregister.R.id.register_toolbar);
            if (toolbar != null) {
                if (getActivity() instanceof AppCompatActivity) {
                    AppCompatActivity act = (AppCompatActivity) getActivity();
                    act.setSupportActionBar(toolbar);
                    if (act.getSupportActionBar() != null) {
                        act.getSupportActionBar().setDisplayShowTitleEnabled(false);
                    }
                }
                toolbar.setTitle("");
                TextView titleLabel = toolbar.findViewById(org.smartregister.R.id.txt_title_label);
                if (titleLabel != null) titleLabel.setVisibility(View.GONE);

                NavigationMenu menu = NavigationMenu.getInstance(getActivity(), null, toolbar);
                if (menu != null && menu.getNavigationAdapter() != null) {
                    menu.getNavigationAdapter().setSelectedView(Constants.DrawerMenu.CHIMWEMWE);
                }
                try {
                    if (menu != null && getActivity() != null) {
                        androidx.drawerlayout.widget.DrawerLayout drawer = menu.getDrawer();
                        androidx.appcompat.graphics.drawable.DrawerArrowDrawable arrow =
                                new androidx.appcompat.graphics.drawable.DrawerArrowDrawable(getActivity());
                        arrow.setColor(android.graphics.Color.WHITE);
                        toolbar.setNavigationIcon(arrow);
                        toolbar.setNavigationOnClickListener(v -> {
                            if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
                        });
                    }
                } catch (Throwable ignored) {}
            }

            View navbarContainer = view.findViewById(org.smartregister.R.id.register_nav_bar_container);
            if (navbarContainer != null) navbarContainer.bringToFront();

            View searchBarLayout = view.findViewById(R.id.search_bar_layout);
            if (searchBarLayout != null) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                searchBarLayout.setLayoutParams(params);
                searchBarLayout.setBackgroundResource(R.color.primary);
                searchBarLayout.setPadding(
                        searchBarLayout.getPaddingLeft(),
                        searchBarLayout.getPaddingTop(),
                        searchBarLayout.getPaddingRight(),
                        (int) org.smartregister.chw.core.utils.Utils.convertDpToPixel(10, getActivity()));
            }

            if (getSearchView() != null) {
                getSearchView().setHint(getString(R.string.search_hint));
                getSearchView().setBackgroundResource(org.smartregister.R.color.white);
                getSearchView().setCompoundDrawablesWithIntrinsicBounds(
                        org.smartregister.R.drawable.ic_action_search, 0, 0, 0);
                getSearchView().setTextColor(getResources().getColor(org.smartregister.R.color.text_black));
            }

            hideIfPresent(view, org.smartregister.R.id.top_right_layout);
            hideIfPresent(view, org.smartregister.R.id.top_left_layout);
            hideIfPresent(view, org.smartregister.R.id.register_sort_filter_bar_layout);
            hideIfPresent(view, org.smartregister.R.id.filter_sort_layout);
            hideIfPresent(view, R.id.opensrp_logo_image_view);

        } catch (Exception e) {
            Log.e(TAG, "setupViews error", e);
        }
    }

    private void hideIfPresent(View root, int id) {
        try {
            View v = root.findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    // ── Search ───────────────────────────────────────────────

    @Override
    protected void updateSearchView() {
        if (getSearchView() != null) {
            getSearchView().removeTextChangedListener(textWatcher);
            getSearchView().removeTextChangedListener(debouncedSearchWatcher);
            getSearchView().addTextChangedListener(debouncedSearchWatcher);
            getSearchView().setOnKeyListener(hideKeyboard);
        }
    }

    @Override
    public void filter(String filterString, String joinTableString,
                       String mainConditionString, boolean qrCode) {
        if (getSearchCancelView() != null) {
            getSearchCancelView().setVisibility(
                    filterString == null || filterString.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
        if (filterString == null || filterString.isEmpty()) {
            Utils.hideKeyboard(getActivity());
        }

        this.filters = filterString;
        this.joinTable = joinTableString;
        this.mainCondition = mainConditionString;

        if (clientAdapter != null) {
            if (clientAdapter.getCurrentlimit() == 0) clientAdapter.setCurrentlimit(20);
            clientAdapter.setCurrentoffset(0);
        }

        showProgressView();
        filterandSortExecute(countBundle());
    }

    private void applySearch(String text) {
        filter(text, "", getMainCondition(), false);
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacks(searchRunnable);
        if (getSearchView() != null) {
            getSearchView().removeTextChangedListener(debouncedSearchWatcher);
        }
        super.onDestroyView();
    }

    // ── Lifecycle ────────────────────────────────────────────

    @Override
    public void onResume() {
        super.onResume();
        updateTitle();
    }

    private void updateTitle() {
        View root = getView();
        if (root == null) return;
        Toolbar toolbar = root.findViewById(org.smartregister.R.id.register_toolbar);
        if (toolbar != null) toolbar.setTitle("");
        TextView titleLabel = root.findViewById(org.smartregister.R.id.txt_title_label);
        if (titleLabel != null) {
            titleLabel.setVisibility(View.VISIBLE);
            titleLabel.setText(getString(R.string.chimwemwe_groups));
        }
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity act = (AppCompatActivity) getActivity();
            if (act.getSupportActionBar() != null) {
                act.getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }
    }

    // ── Adapter ──────────────────────────────────────────────

    /**
     * Run the COUNT query off the main thread to avoid blocking on a SQLite write lock held
     * by OpenSRP's background sync, which would otherwise cause an ANR.
     */
    @Override
    public void countExecute() {
        Threading.io(() -> {
            int count = 0;
            try {
                Cursor cursor = context().commonrepository(TABLE)
                        .rawCustomQueryForAdapter("SELECT COUNT(*) FROM " + TABLE);
                if (cursor != null) {
                    cursor.moveToFirst();
                    count = cursor.getInt(0);
                    cursor.close();
                }
            } catch (Exception e) {
                Timber.e(e, "countExecute");
            }
            final int finalCount = count;
            Threading.main(() -> {
                if (clientAdapter != null) {
                    clientAdapter.setTotalcount(finalCount);
                    clientAdapter.setCurrentlimit(20);
                }
            });
        });
    }

    @Override
    public void initializeAdapter() {
        try {
            if (clientsView == null) {
                View root = getView();
                if (root != null) {
                    androidx.recyclerview.widget.RecyclerView rv =
                            root.findViewById(org.smartregister.R.id.recycler_view);
                    if (rv != null) {
                        clientsView = rv;
                        if (clientsView.getLayoutManager() == null && getContext() != null) {
                            clientsView.setLayoutManager(new LinearLayoutManager(getContext()));
                        }
                        clientsView.setHasFixedSize(true);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (clientsView == null) {
            Log.e(TAG, "RecyclerView not found; skipping adapter initialization");
            return;
        }

        ChimwemweRegisterProvider provider =
                new ChimwemweRegisterProvider(requireContext(), registerActionHandler, paginationViewHandler);
        clientAdapter = new RecyclerViewPaginatedAdapter(
                null, provider, context().commonrepository(TABLE));
        clientAdapter.setCurrentlimit(20);
        clientsView.setAdapter(clientAdapter);
    }

    @Override
    protected void onResumption() {
        super.onResumption();
        if (clientAdapter != null) {
            try { clientAdapter.notifyDataSetChanged(); } catch (Exception ignored) {}
        }
    }

    // ── Navigation ───────────────────────────────────────────

    @Override
    protected void onViewClicked(View view) {
        Object tag = view.getTag();
        if (!(tag instanceof CommonPersonObjectClient)) return;
        CommonPersonObjectClient client = (CommonPersonObjectClient) tag;
        String idStr = Utils.getValue(client.getColumnmaps(), "id", false);
        long groupId = -1L;
        try { groupId = Long.parseLong(idStr); } catch (Exception ignored) {}
        Intent intent = new Intent(requireContext(), HotspotGroupDetailActivity.class);
        intent.putExtra("group_id", groupId);
        startActivity(intent);
    }

    // ── Query params ─────────────────────────────────────────

    @Override
    protected String getMainCondition() {
        return "1=1";
    }

    @Override
    protected String getDefaultSortQuery() {
        return "group_name ASC";
    }

    @Override
    protected void startRegistration() {}

    // ── Contract stubs ───────────────────────────────────────

    @Override public void setUniqueID(String s) {}
    @Override public void setAdvancedSearchFormData(HashMap<String, String> map) {}
    @Override public void showNotFoundPopup(String s) {}

    @Override
    public void showProgressView() {
        try {
            View root = getView();
            if (root == null) return;
            View pb = root.findViewById(R.id.client_list_progress);
            if (pb != null) pb.setVisibility(View.VISIBLE);
        } catch (Exception ignored) {}
    }

    @Override
    public void hideProgressView() {
        try {
            View root = getView();
            if (root == null) return;
            View pb = root.findViewById(R.id.client_list_progress);
            if (pb != null) pb.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }
}
