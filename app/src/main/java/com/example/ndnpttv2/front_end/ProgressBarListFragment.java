package com.example.ndnpttv2.front_end;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.example.ndnpttv2.R;

import java.util.ArrayList;

public class ProgressBarListFragment extends Fragment {

    private static final String TAG = "ProgressBarListFragment";

    private ArrayList<ProgressBarRenderingInfo> progressBarList_;

    private FragmentManager fragmentManager_;
    private LinearLayout progressBarsLayout_;

    private class ProgressBarRenderingInfo {
        public ProgressBarRenderingInfo(ProgressBarFragment progressBarFragment) {
            this.progressBarFragment = progressBarFragment;
        }
        ProgressBarFragment progressBarFragment;
        boolean rendered = false;
        FrameLayout frameLayout;
        int frameLayoutId;
    }

    public ProgressBarListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        progressBarList_ = new ArrayList<>();

        fragmentManager_ = getFragmentManager();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_progress_bar_list, container, false);

        progressBarsLayout_ = (LinearLayout) view.findViewById(R.id.progress_bars_layout);

        // Inflate the layout for this fragment
        return view;

    }

    void addProgressBar(ProgressBarFragment progressBarFragment) {
        ProgressBarRenderingInfo renderingInfo = new ProgressBarRenderingInfo(progressBarFragment);
        progressBarList_.add(renderingInfo);

        FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setId(View.generateViewId());
        int frameLayoutId = frameLayout.getId();
        progressBarsLayout_.addView(frameLayout, 0);

        renderingInfo.frameLayout = frameLayout;
        renderingInfo.frameLayoutId = frameLayoutId;

        try {
            FragmentTransaction transaction = fragmentManager_.beginTransaction();
            transaction.add(frameLayoutId, progressBarFragment, progressBarFragment.getStreamName().toString());
            transaction.commit();
        }
        catch (IllegalStateException e) {
            Log.e(TAG, "Failed to add progress bar for " + progressBarFragment.getStreamName().toString() + " to UI, error: " + e.getMessage());
            return;
        }

        renderingInfo.rendered = true;
    }

    public void notifyActivityResumed() {
        for (ProgressBarRenderingInfo renderingInfo: progressBarList_) {
            if (!renderingInfo.rendered) {
                try {
                    FragmentTransaction transaction = fragmentManager_.beginTransaction();
                    transaction.add(renderingInfo.frameLayoutId, renderingInfo.progressBarFragment,
                            renderingInfo.progressBarFragment.getStreamName().toString());
                    transaction.commit();
                }
                catch (IllegalStateException e) {
                    Log.e(TAG, "Failed to add progress bar for " +
                            renderingInfo.progressBarFragment.getStreamName().toString() + " to UI, error: " + e.getMessage());
                    return;
                }

                renderingInfo.rendered = true;
            }
        }
    }

}