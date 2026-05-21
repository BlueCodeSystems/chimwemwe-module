package com.bluecodeltd.chimwemwe.chw.fragment;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bluecodeltd.chimwemwe.chw.util.Threading;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.activity.HotspotGroupDetailActivity;
import com.bluecodeltd.chimwemwe.chw.contract.ChimwemweRegisterFragmentContract;
import com.bluecodeltd.chimwemwe.chw.presenter.ChimwemweRegisterPresenter;
import com.bluecodeltd.chimwemwe.chw.provider.ChimwemweRegisterProvider;
import com.bluecodeltd.chimwemwe.chw.util.Constants;
import com.bluecodeltd.chimwemwe.chw.fragment.BaseSafeRegisterFragment;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the dedicated Chimwemwe layout instead of the shared base register layout
        // so other registers (Family, ANC, HTS, etc.) are not affected.
        View view = inflater.inflate(R.layout.fragment_chimwemwe_register, container, false);
        setupViews(view);
        return view;
    }

    @Override
    public void setupViews(View view) {
        try {
            // Seed `filters` with the base delete_status predicate BEFORE the framework's
            // initial countExecute/filterandSortExecute run inside super.setupViews(view).
            // Without this, the very first query has no WHERE clause and would include
            // soft-deleted rows. buildSqlFilter("") returns just the WHERE predicate;
            // once the user types in the search bar, filter() will replace this value.
            if (filters == null || filters.isEmpty()) {
                this.filters = buildSqlFilter("");
            }
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
                if (titleLabel != null) titleLabel.setVisibility(View.VISIBLE);

                NavigationMenu menu = NavigationMenu.getInstance(getActivity(), null, toolbar);
                if (menu != null && menu.getNavigationAdapter() != null) {
                    menu.getNavigationAdapter().setSelectedView(Constants.DrawerMenu.CHIMWEMWE);
                }
                applyDrawerNavigation(toolbar, menu);
            }

            View navbarContainer = view.findViewById(org.smartregister.R.id.register_nav_bar_container);
            if (navbarContainer != null) navbarContainer.bringToFront();

            if (getSearchView() != null) {
                getSearchView().setCompoundDrawablesWithIntrinsicBounds(
                        org.smartregister.R.drawable.ic_action_search, 0, 0, 0);
                getSearchView().setTextColor(getResources().getColor(R.color.chimwemwe_text_primary));
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

        this.filters = buildSqlFilter(filterString);
        this.joinTable = joinTableString;
        this.mainCondition = mainConditionString;

        if (clientAdapter != null) {
            if (clientAdapter.getCurrentlimit() == 0) clientAdapter.setCurrentlimit(20);
            clientAdapter.setCurrentoffset(0);
        }

        showProgressView();
        filterandSortExecute(countBundle());
    }

    private String buildSqlFilter(String filterString) {
        String base = "WHERE (ec_chimwemwe_group.delete_status IS NULL OR ec_chimwemwe_group.delete_status <> '1')";
        if (filterString == null || filterString.trim().isEmpty()) {
            return base;
        }

        String escaped = filterString.trim()
                .replace("'", "''")
                .replace("%", "\\%")
                .replace("_", "\\_");
        // Qualify group_id with the table name to avoid ambiguity with the JOIN
        // subquery aliases that also expose a group_id column.
        return base + " AND ("
                + "ec_chimwemwe_group.group_name LIKE '%" + escaped + "%' ESCAPE '\\' "
                + "OR ec_chimwemwe_group.hotspot_name LIKE '%" + escaped + "%' ESCAPE '\\' "
                + "OR ec_chimwemwe_group.group_id LIKE '%" + escaped + "%' ESCAPE '\\' "
                + ")";
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
        if (toolbar != null) {
            toolbar.setTitle("");
            applyDrawerNavigation(toolbar, NavigationMenu.getInstance(getActivity(), null, toolbar));
        }
        TextView titleLabel = root.findViewById(org.smartregister.R.id.txt_title_label);
        if (titleLabel != null) {
            titleLabel.setVisibility(View.VISIBLE);
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
                String joinClause =
                        " LEFT JOIN (SELECT group_id, COUNT(*) AS p_count FROM ec_chimwemwe_participant" +
                        " WHERE (delete_status IS NULL OR delete_status <> '1') GROUP BY group_id) AS participant_counts" +
                        " ON participant_counts.group_id = ec_chimwemwe_group.group_id" +
                        " LEFT JOIN (SELECT group_id, COUNT(DISTINCT session_number) AS s_count FROM ec_chimwemwe_session_attendance" +
                        " WHERE (delete_status IS NULL OR delete_status <> '1') GROUP BY group_id) AS attendance_counts" +
                        " ON attendance_counts.group_id = ec_chimwemwe_group.group_id";
                // When the user has typed in the search bar, `filters` is already a full
                // WHERE clause (built by buildSqlFilter) that includes the delete_status
                // predicate. On initial load, `filters` is empty — we must add our own.
                String whereClause = (filters == null || filters.isEmpty())
                        ? " WHERE (ec_chimwemwe_group.delete_status IS NULL OR ec_chimwemwe_group.delete_status <> '1')"
                        : " " + filters;
                String query = "SELECT COUNT(*) FROM " + TABLE + joinClause + whereClause;
                Cursor cursor = context().commonrepository(TABLE)
                        .rawCustomQueryForAdapter(query);
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
        // Re-run the cursor query (not just notify) so rows soft-deleted from child
        // screens (group/participant detail) disappear when we return to the register.
        try { countExecute(); } catch (Exception e) { Timber.e(e, "onResumption: countExecute"); }
        try { filterandSortInInitializeQueries(); } catch (Exception e) { Timber.e(e, "onResumption: filterandSort"); }
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
        Intent intent = new Intent(requireContext(), HotspotGroupDetailActivity.class);
        String groupId = Utils.getValue(client.getColumnmaps(), "group_id", false);
        if (groupId == null || groupId.trim().isEmpty()) {
            groupId = Utils.getValue(client.getColumnmaps(), "id", false);
        }
        intent.putExtra(HotspotGroupDetailActivity.EXTRA_GROUP_ID, groupId);
        startActivity(intent);
    }

    // ── Query params ─────────────────────────────────────────

    @Override
    protected String getMainCondition() {
        return "(ec_chimwemwe_group.delete_status IS NULL OR ec_chimwemwe_group.delete_status <> '1')";
    }

    @Override
    protected String getDefaultSortQuery() {
        return "group_name ASC";
    }

    @Override
    protected boolean isValidFilterForFts(org.smartregister.commonregistry.CommonRepository commonRepository) {
        return false;
    }

    private void applyDrawerNavigation(Toolbar toolbar, NavigationMenu menu) {
        try {
            if (toolbar == null || menu == null || getActivity() == null) return;
            androidx.drawerlayout.widget.DrawerLayout drawer = menu.getDrawer();
            androidx.appcompat.graphics.drawable.DrawerArrowDrawable arrow =
                    new androidx.appcompat.graphics.drawable.DrawerArrowDrawable(getActivity());
            arrow.setColor(android.graphics.Color.WHITE);
            arrow.setProgress(0f);
            toolbar.setNavigationIcon(arrow);
            toolbar.setNavigationContentDescription("Open drawer");
            toolbar.setNavigationOnClickListener(v -> {
                if (drawer != null) drawer.openDrawer(androidx.core.view.GravityCompat.START);
            });
        } catch (Throwable ignored) {}
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
