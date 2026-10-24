package com.lumisound.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lumisound.android.R
import com.lumisound.android.playback.PlaybackService
import com.lumisound.android.ui.MainActivity

/**
 * A home-screen "now playing" widget -- title/artist, play/pause, skip
 * next/previous. iOS parity (`WidgetDataService`/`LumisoundWidget`), but a
 * much simpler architecture: iOS's widget runs in a separate extension
 * process and reads shared App Group storage because WidgetKit requires
 * that; a classic Android `AppWidgetProvider`'s `RemoteViews` are just
 * pushed directly from this same app process (`NowPlayingWidgetUpdater`,
 * called from `PlaybackService` on every track/play-state change), with no
 * separate storage layer needed.
 *
 * Text-only for now, deliberately -- no album artwork. `RemoteViews` has no
 * async image loading of its own; fetching a bitmap synchronously inside a
 * background-restricted widget update is a real correctness risk to take on
 * without a device to confirm it against (timeouts, main-thread violations,
 * stale images after an app-process death). Worth adding later with a
 * proper async bitmap cache, not guessed at here.
 */
class NowPlayingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // No live player state to read from this callback alone (it fires on
        // first placement/reboot, before any track has necessarily played) --
        // show the empty state; PlaybackService pushes the real state the
        // moment it has one.
        val views = NowPlayingWidgetUpdater.buildViews(context, title = null, artist = null, isPlaying = false)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }
}

object NowPlayingWidgetUpdater {
    fun update(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildViews(context, title, artist, isPlaying)
        manager.updateAppWidget(ids, views)
    }

    fun buildViews(context: Context, title: String?, artist: String?, isPlaying: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
        views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_not_playing))
        views.setTextViewText(R.id.widget_artist, artist ?: context.getString(R.string.widget_open_app))
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        views.setOnClickPendingIntent(R.id.widget_play_pause, servicePendingIntent(context, PlaybackService.ACTION_WIDGET_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_next, servicePendingIntent(context, PlaybackService.ACTION_WIDGET_NEXT))
        views.setOnClickPendingIntent(R.id.widget_previous, servicePendingIntent(context, PlaybackService.ACTION_WIDGET_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent(context))
        views.setOnClickPendingIntent(R.id.widget_artist, openAppPendingIntent(context))
        return views
    }

    private fun servicePendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(context, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
