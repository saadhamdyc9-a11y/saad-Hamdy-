package com.example

import com.example.domain.model.DownloadStatus
import com.example.domain.model.MediaFormat
import com.example.domain.model.MediaType
import com.example.media.provider.DirectMediaProvider
import com.example.media.provider.InstagramSourceProvider
import com.example.media.provider.MediaAnalyzer
import com.example.media.provider.TikTokSourceProvider
import com.example.media.provider.TwitterXSourceProvider
import com.example.media.provider.YouTubeSourceProvider
import com.example.utils.StorageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testUrlValidation() {
        val analyzer = MediaAnalyzer()

        val emptyResult = analyzer.validateUrl("")
        assertTrue(emptyResult is MediaAnalyzer.ValidationResult.Error)

        val invalidScheme = analyzer.validateUrl("ftp://example.com/video.mp4")
        assertTrue(invalidScheme is MediaAnalyzer.ValidationResult.Error)

        val validHttps = analyzer.validateUrl("https://example.com/video.mp4")
        assertTrue(validHttps is MediaAnalyzer.ValidationResult.Valid)
        assertEquals("https://example.com/video.mp4", (validHttps as MediaAnalyzer.ValidationResult.Valid).url)
    }

    @Test
    fun testSourceDetection() {
        val ytProvider = YouTubeSourceProvider()
        assertTrue(ytProvider.supports("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(ytProvider.supports("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(ytProvider.supports("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertFalse(ytProvider.supports("https://vimeo.com/12345"))

        assertEquals("dQw4w9WgXcQ", ytProvider.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", ytProvider.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))

        val ttProvider = TikTokSourceProvider()
        assertTrue(ttProvider.supports("https://www.tiktok.com/@user/video/1234567890"))
        assertFalse(ttProvider.supports("https://example.com/video.mp4"))

        val igProvider = InstagramSourceProvider()
        assertTrue(igProvider.supports("https://www.instagram.com/reel/C12345/"))
        assertFalse(igProvider.supports("https://twitter.com/post/1"))

        val twProvider = TwitterXSourceProvider()
        assertTrue(twProvider.supports("https://x.com/user/status/12345"))
        assertTrue(twProvider.supports("https://twitter.com/user/status/12345"))
        assertFalse(twProvider.supports("https://facebook.com/watch"))

        val directProvider = DirectMediaProvider()
        assertTrue(directProvider.supports("https://example.com/test.mp4"))
        assertTrue(directProvider.supports("https://example.com/audio.mp3"))
        assertTrue(directProvider.supports("https://example.com/stream.m4a?key=123"))
    }

    @Test
    fun testFilenameSanitization() {
        val dangerousName = "my/cool:video*title?with\"quotes<and>pipes|here"
        val safe = StorageUtils.sanitizeFilename(dangerousName, ".mp4")
        assertFalse(safe.contains("/"))
        assertFalse(safe.contains(":"))
        assertFalse(safe.contains("*"))
        assertFalse(safe.contains("?"))
        assertFalse(safe.contains("\""))
        assertFalse(safe.contains("<"))
        assertFalse(safe.contains(">"))
        assertFalse(safe.contains("|"))
        assertTrue(safe.endsWith(".mp4"))

        val emptyName = StorageUtils.sanitizeFilename("", "mp4")
        assertTrue(safe.isNotBlank())
        assertTrue(emptyName.endsWith(".mp4"))
    }

    @Test
    fun testStorageFormatting() {
        assertEquals("0 B", StorageUtils.formatBytes(0))
        assertEquals("500 B", StorageUtils.formatBytes(500))
        assertEquals("1.0 KB", StorageUtils.formatBytes(1024))
        assertEquals("1.5 MB", StorageUtils.formatBytes(1_572_864))
        assertEquals("1.00 GB", StorageUtils.formatBytes(1_073_741_824))

        assertEquals("00:45", StorageUtils.formatDuration(45))
        assertEquals("02:30", StorageUtils.formatDuration(150))
        assertEquals("01:05:20", StorageUtils.formatDuration(3920))
    }

    @Test
    fun testMediaFormatModels() {
        val format = MediaFormat(
            id = "test_1",
            format = "MP4",
            quality = "1080p",
            mediaType = MediaType.VIDEO,
            estimatedSizeBytes = 50_000_000L,
            downloadUrl = "https://example.com/file.mp4",
            mimeType = "video/mp4"
        )
        assertEquals("1080p", format.quality)
        assertEquals(MediaType.VIDEO, format.mediaType)
        assertEquals("MP4", format.format)
    }
}
