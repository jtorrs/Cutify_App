package com.example.online_alot;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

/**
 * Loads barber profile images: synced HTTPS URL (all devices) or local content/file URIs (same device).
 */
public final class BarberPhotoHelper {

    private BarberPhotoHelper() {}

    public static void loadBarberPhoto(Context context, Barber barber, ImageView target) {
        if (target == null) {
            return;
        }
        String url = barber != null ? barber.getPhotoUrl() : "";
        if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
            // Fill the profile frame completely so no black/empty area appears.
            target.setScaleType(ImageView.ScaleType.CENTER_CROP);
            RequestOptions opts = new RequestOptions()
                    .centerCrop()
                    .placeholder(R.drawable.ic_nav_profile)
                    .error(R.drawable.ic_nav_profile)
                    .diskCacheStrategy(DiskCacheStrategy.ALL);
            Glide.with(context.getApplicationContext())
                    .load(url.trim())
                    .apply(opts)
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(target);
            return;
        }
        target.setImageResource(R.drawable.ic_nav_profile);
    }
}
