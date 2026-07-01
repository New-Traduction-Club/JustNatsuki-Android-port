package org.renpy.android

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import java.io.File

/**
 * Encapsulated UI helpers for rendering desktop notification toasts on top of any active Activity.
 */
object DesktopNotificationUI {

    fun getOrCreateToastContainer(activity: Activity): ViewGroup {
        var root: ViewGroup? = null
        try {
            val field = activity::class.java.getField("mFrameLayout")
            root = field.get(activity) as? ViewGroup
        } catch (e: Exception) {
        }
        
        if (root == null) {
            root = activity.findViewById<ViewGroup>(android.R.id.content)
        }
        
        var container = root?.findViewById<ViewGroup>(R.id.toastContainer)
        if (container == null && root != null) {
            container = android.widget.LinearLayout(activity).apply {
                id = R.id.toastContainer
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM
                elevation = 16f * activity.resources.displayMetrics.density
                
                val params = android.widget.FrameLayout.LayoutParams(
                    (320 * activity.resources.displayMetrics.density).toInt(),
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    rightMargin = (16 * activity.resources.displayMetrics.density).toInt()
                    bottomMargin = (8 * activity.resources.displayMetrics.density).toInt()
                }
                layoutParams = params
            }
            root.addView(container)
        }
        return container ?: root!!
    }

    fun showNotificationToast(activity: Activity, title: String, message: String, imagePath: String?) {
        activity.runOnUiThread {
            try {
                val container = getOrCreateToastContainer(activity)
                val inflater = LayoutInflater.from(activity)
                val toastView = inflater.inflate(R.layout.layout_notification_toast, container, false)
                
                toastView.findViewById<TextView>(R.id.toastTitle).text = title
                toastView.findViewById<TextView>(R.id.toastMessage).text = message

                val imgAvatar = toastView.findViewById<ImageView>(R.id.toastAvatar)
                if (!imagePath.isNullOrEmpty()) {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            imgAvatar.setImageBitmap(bitmap)
                        } else {
                            imgAvatar.setImageResource(R.drawable.ic_notifications)
                        }
                    } else {
                        imgAvatar.setImageResource(R.drawable.ic_notifications)
                    }
                } else {
                    imgAvatar.setImageResource(R.drawable.ic_notifications)
                }

                toastView.findViewById<View>(R.id.toastClose).setOnClickListener {
                    slideOutAndRemoveToast(container, toastView)
                }

                container.addView(toastView)

                val density = activity.resources.displayMetrics.density
                toastView.translationX = 340f * density
                toastView.alpha = 0f
                toastView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()

                // max 3
                if (container.childCount > 3) {
                    val oldest = container.getChildAt(0)
                    slideOutAndRemoveToast(container, oldest)
                }

                toastView.postDelayed({
                    if (toastView.parent != null) {
                        slideOutAndRemoveToast(container, toastView)
                    }
                }, 5000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun slideOutAndRemoveToast(container: ViewGroup, view: View) {
        val density = container.context.resources.displayMetrics.density
        view.animate()
            .translationX(340f * density)
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                container.removeView(view)
            }
            .start()
    }
}
