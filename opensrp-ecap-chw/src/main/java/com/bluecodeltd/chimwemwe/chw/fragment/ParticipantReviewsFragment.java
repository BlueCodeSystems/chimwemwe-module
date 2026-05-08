package com.bluecodeltd.chimwemwe.chw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bluecodeltd.chimwemwe.chw.R;
import com.bluecodeltd.chimwemwe.chw.adapter.ParticipantReviewsAdapter;
import com.bluecodeltd.chimwemwe.chw.dao.ChimwemweParticipantReviewDao;
import com.bluecodeltd.chimwemwe.chw.model.chimwemweParticipantReviewModel;
import com.bluecodeltd.chimwemwe.chw.util.ChimwemweFormUtils;
import com.bluecodeltd.chimwemwe.chw.util.Threading;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONObject;
import org.smartregister.chw.core.utils.CoreJsonFormUtils;
import org.smartregister.util.FormUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class ParticipantReviewsFragment extends Fragment implements ParticipantProfileSection {

    private static final int REQ_REVIEW = 3002;

    private TextView tvEmpty;
    private RecyclerView recycler;
    private ParticipantReviewsAdapter adapter;

    private List<chimwemweParticipantReviewModel> items = new ArrayList<>();

    public ParticipantReviewsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_participant_reviews, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvEmpty = view.findViewById(R.id.tv_empty);
        recycler = view.findViewById(R.id.recycler);

        adapter = new ParticipantReviewsAdapter(new ParticipantReviewsAdapter.Listener() {
            @Override
            public void onEdit(@NonNull chimwemweParticipantReviewModel review) {
                launchReviewForm(review);
            }

            @Override
            public void onDelete(@NonNull chimwemweParticipantReviewModel review) {
                confirmDelete(review);
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recycler.setAdapter(adapter);

        refreshContent();
    }

    public void launchReviewForm(@Nullable chimwemweParticipantReviewModel existing) {
        if (!(getActivity() instanceof com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity)) return;
        com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity host =
                (com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity) getActivity();

        Threading.io(() -> {
            try {
                FormUtils formUtils = new FormUtils(requireContext());
                JSONObject form = formUtils.getFormJson("chimwemwe_participant_review");
                if (form == null) return;

                if (host.getParticipant() != null) {
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", host.getParticipant().getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id", host.getParticipant().getParticipantId());
                }

                if (existing != null) {
                    try {
                        CoreJsonFormUtils.populateJsonForm(
                                form,
                                new ObjectMapper().convertValue(existing, Map.class)
                        );
                    } catch (Exception e) {
                        Timber.w(e, "populateJsonForm review");
                    }
                    String baseEntityId = existing.getBase_entity_id();
                    if (baseEntityId != null && !baseEntityId.trim().isEmpty()) form.put("entity_id", baseEntityId);
                }

                Threading.main(() -> {
                    try {
                        android.content.Intent intent = new android.content.Intent(
                                requireContext(),
                                org.smartregister.family.util.Utils.metadata().familyFormActivity
                        );
                        com.vijay.jsonwizard.domain.Form cfg = new com.vijay.jsonwizard.domain.Form();
                        cfg.setWizard(true);
                        cfg.setHideSaveLabel(true);
                        cfg.setNextLabel(getString(R.string.next));
                        cfg.setPreviousLabel(getString(R.string.previous));
                        cfg.setSaveLabel(getString(R.string.submit));
                        cfg.setNavigationBackground(R.color.chimwemwe_primary);
                        intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.FORM, cfg);
                        intent.putExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON, form.toString());
                        startActivityForResult(intent, REQ_REVIEW);
                    } catch (Exception e) {
                        Timber.e(e, "launchReviewForm startActivityForResult");
                    }
                });
            } catch (Exception e) {
                Timber.e(e, "launchReviewForm");
            }
        });
    }

    @Override
    public void refreshContent() {
        if (!(getActivity() instanceof com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity)) return;
        com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity host =
                (com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity) getActivity();
        String participantCode = host.getParticipant() != null ? host.getParticipant().getParticipantId() : null;
        if (participantCode == null || participantCode.trim().isEmpty()) {
            items = new ArrayList<>();
            render();
            host.setReviewsCount(0);
            return;
        }

        Threading.io(() -> {
            List<chimwemweParticipantReviewModel> loaded = null;
            int count = 0;
            try {
                loaded = ChimwemweParticipantReviewDao.getParticipantReviews(participantCode);
                count = ChimwemweParticipantReviewDao.countParticipantReviews(participantCode);
            } catch (Exception e) {
                Timber.e(e, "Load participant reviews failed");
            }
            List<chimwemweParticipantReviewModel> finalLoaded = loaded != null ? loaded : new ArrayList<>();
            int finalCount = count;
            Threading.main(() -> {
                items = finalLoaded;
                render();
                host.setReviewsCount(finalCount);
            });
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_REVIEW) return;
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return;

        String jsonString = data.getStringExtra(com.vijay.jsonwizard.constants.JsonFormConstants.JSON_FORM_KEY.JSON);
        if (jsonString == null) return;

        if (!(getActivity() instanceof com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity)) return;
        com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity host =
                (com.bluecodeltd.chimwemwe.chw.activity.ChimwemweParticipantProfileActivity) getActivity();

        Threading.io(() -> {
            try {
                JSONObject form = new JSONObject(jsonString);
                boolean isEdit = !form.optString("entity_id", "").isEmpty();
                if (host.getParticipant() != null) {
                    ChimwemweFormUtils.ensureFieldValue(form, "group_id", host.getParticipant().getGroupId());
                    ChimwemweFormUtils.ensureFieldValue(form, "participant_id", host.getParticipant().getParticipantId());
                }
                ChimwemweFormUtils.saveRegistration(
                        ChimwemweFormUtils.processRegistration(form, "ec_chimwemwe_review", null),
                        isEdit
                );
                Threading.main(() -> {
                    host.reloadParticipant();
                    refreshContent();
                });
            } catch (Exception e) {
                Timber.e(e, "onActivityResult review");
            }
        });
    }

    private void render() {
        if (tvEmpty == null || recycler == null || adapter == null) return;
        boolean empty = items == null || items.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.setData(items);
    }

    private void confirmDelete(chimwemweParticipantReviewModel review) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete review?")
                .setMessage("This will permanently delete this review.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> Threading.io(() -> {
                    ChimwemweParticipantReviewDao.deleteReview(review.getBase_entity_id());
                    Threading.main(this::refreshContent);
                }))
                .show();
    }

    public interface ParticipantFormHost {
        void launchReviewForm(@Nullable chimwemweParticipantReviewModel review);
        void reloadParticipant();
    }
}
