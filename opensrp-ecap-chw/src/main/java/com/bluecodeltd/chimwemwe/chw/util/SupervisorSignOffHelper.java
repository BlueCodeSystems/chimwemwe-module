package com.bluecodeltd.chimwemwe.chw.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bluecodeltd.chimwemwe.chw.R;
import com.github.gcacace.signaturepad.views.SignaturePad;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class SupervisorSignOffHelper {

    public interface Callback {
        void onCaptured(String signature, String gps);
    }

    private interface GpsSink {
        void accept(String gps);
    }

    private SupervisorSignOffHelper() {}

    public static void prompt(Activity activity, Callback callback) {
        if (activity == null || callback == null) return;

        View dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_supervisor_signature, null);
        SignaturePad pad = dialogView.findViewById(R.id.dialog_signature_pad);
        TextView tvLocation = dialogView.findViewById(R.id.dialog_location_text);
        final String[] supervisorGps = {""};

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        dialogView.findViewById(R.id.dialog_signature_clear).setOnClickListener(v -> pad.clear());
        dialogView.findViewById(R.id.dialog_signature_cancel).setOnClickListener(v -> {
            Toast.makeText(activity, "Supervisor sign-off cancelled. Review not saved.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.dialog_location_capture)
                .setOnClickListener(v -> captureLocation(activity, tvLocation, gps -> supervisorGps[0] = gps));
        dialogView.findViewById(R.id.dialog_signature_save).setOnClickListener(v -> {
            if (pad.isEmpty()) {
                Toast.makeText(activity, "Supervisor signature is required.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (supervisorGps[0] == null || supervisorGps[0].trim().isEmpty()) {
                Toast.makeText(activity, "Capture the supervisor's GPS before saving.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String signature = Base64.encodeToString(
                        bitmapToPng(pad.getSignatureBitmap()), Base64.NO_WRAP);
                dialog.dismiss();
                callback.onCaptured(signature, supervisorGps[0]);
            } catch (Exception e) {
                Toast.makeText(activity, "Could not capture signature. Please retry.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private static void captureLocation(Activity activity, TextView readout, GpsSink sink) {
        if (readout == null || sink == null || activity == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            readout.setText("Location permission is required.");
            Toast.makeText(activity, "Location permission is required to capture supervisor GPS.", Toast.LENGTH_SHORT).show();
            return;
        }

        readout.setText("Acquiring location... keep GPS on and wait for a fix.");
        LocationManager lm = (LocationManager) activity.getSystemService(Activity.LOCATION_SERVICE);
        if (lm == null) {
            readout.setText("Location service is unavailable on this device.");
            return;
        }

        final boolean[] delivered = {false};
        Handler handler = new Handler(Looper.getMainLooper());
        LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (location == null || delivered[0]) return;
                delivered[0] = true;
                try { lm.removeUpdates(this); } catch (Exception ignored) {}
                publishLocation(location, readout, sink);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };

        boolean requested = false;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {}

        if (!requested) {
            readout.setText("Enable device location/GPS, then tap Get location again.");
            return;
        }

        handler.postDelayed(() -> {
            if (delivered[0]) return;
            delivered[0] = true;
            try { lm.removeUpdates(listener); } catch (Exception ignored) {}
            Location fallback = bestLastKnownLocation(lm);
            if (fallback != null) {
                publishLocation(fallback, readout, sink);
            } else {
                readout.setText("Still acquiring location. Move near a window/outdoors and tap Get location again.");
            }
        }, 15000L);
    }

    private static Location bestLastKnownLocation(LocationManager lm) {
        Location best = null;
        try { best = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); } catch (Exception ignored) {}
        try {
            Location network = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (best == null || (network != null && network.getTime() > best.getTime())) best = network;
        } catch (Exception ignored) {}
        return best;
    }

    private static void publishLocation(Location loc, TextView readout, GpsSink sink) {
        String gps = String.format(Locale.US, "%.6f,%.6f", loc.getLatitude(), loc.getLongitude());
        sink.accept(gps);
        readout.setText(String.format(Locale.US,
                "Latitude: %.5f\nLongitude: %.5f\nAccuracy: %dm",
                loc.getLatitude(), loc.getLongitude(), Math.round(loc.getAccuracy())));
    }

    private static byte[] bitmapToPng(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return out.toByteArray();
    }
}
