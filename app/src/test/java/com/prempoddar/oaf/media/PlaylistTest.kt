package com.prempoddar.oaf.media

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaylistTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun files(vararg names: String): File {
        val dir = tmp.newFolder("album")
        names.forEach { File(dir, it).writeText("x") }
        return dir
    }

    @Test fun queuesTheSameKindInNaturalOrderStartingAtTheOpenedFile() {
        val dir = files("10 Ten.mp3", "2 Two.mp3", "1 One.flac", "cover.jpg", "clip.mp4", ".hidden.mp3")
        val q = Playlist.around(File(dir, "2 Two.mp3"))
        assertThat(q.items.map { it.name }).containsExactly("1 One.flac", "2 Two.mp3", "10 Ten.mp3").inOrder()
        assertThat(q.start).isEqualTo(1)
    }

    @Test fun videosQueueOnlyVideos() {
        val dir = files("a.mp4", "b.mkv", "song.mp3")
        assertThat(Playlist.around(File(dir, "b.mkv")).items.map { it.name }).containsExactly("a.mp4", "b.mkv").inOrder()
    }

    @Test fun findsSidecarSubtitles() {
        val dir = files("Film.mp4", "Film.srt", "Film.en.vtt", "Other.srt")
        assertThat(Playlist.subtitlesFor(File(dir, "Film.mp4")).map { it.name }).containsExactly("Film.en.vtt", "Film.srt")
    }

    @Test fun languageFromSubtitleName() {
        assertThat(Playlist.language(File("Film.en.vtt"), "Film")).isEqualTo("en")
        assertThat(Playlist.language(File("Film.srt"), "Film")).isNull()
    }
}
