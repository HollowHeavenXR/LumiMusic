package com.lumisound.android.ui

/**
 * Screens pushed on top of a tab. A plain stack held by the shell rather than a navigation
 * library: every route here is one level of "open this, then go back", and the system back
 * gesture pops it.
 */
sealed interface Route {
    data object Settings : Route
    /** A Library crate; [playlistId] opens straight into one playlist. */
    data class Library(val section: com.lumisound.android.ui.screens.library.LibrarySection, val playlistId: String? = null) : Route
    data object Equalizer : Route
    data object GalleryBackground : Route
    data object Downloads : Route
    data object Diagnostics : Route
    data object DuplicateFinder : Route
    data object Stats : Route
    data object Rewind : Route
    data object Achievements : Route
    data object Notifications : Route
    data object Scrobbling : Route
    data object Podcasts : Route
    data class Podcast(val feedUrl: String, val title: String, val artworkUrl: String?) : Route
    data class Profile(val userId: String, val name: String) : Route
}
