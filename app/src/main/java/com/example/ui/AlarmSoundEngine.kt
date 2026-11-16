package com.example.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.data.StorageAndBackupCoordinator
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

enum class AlarmSoundCategory(val label: String) {
    BOTANICAL("Botanical Melodies"),
    STUDIO_CHIMES("Warm Studio Chimes"),
    ALERTS("Clear & Classic Alerts"),
    PERSONAL_DEVICE("Voice & Device Ringtones")
}

data class ChimeNote(
    val freqHz: Double,
    val durationMs: Int,
    val gapAfterMs: Int = 55,
    val harmonicWeight: Double = 0.28,
    val modulationHz: Double = 0.0
)

data class AlarmSoundPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: AlarmSoundCategory,
    val notes: List<ChimeNote> = emptyList(),
    val loopPauseMs: Long = 950L
)

object AlarmSoundCatalog {

    const val SOUND_APP_DEFAULT = "App Default"
    const val SOUND_SAVED_AUDIO = "Play Saved Task Audio"
    const val SOUND_ZEN_BOWL = "Zen Bowl Resonance"
    const val SOUND_DIGITAL_BEEP = "Digital Beep Pitch"
    const val CUSTOM_RINGTONE_PREFIX = "ringtone://"

    val presets: List<AlarmSoundPreset> = listOf(
        // Botanical Melodies
        AlarmSoundPreset(
            id = "Nordic Forest Chime",
            title = "Nordic Forest Chime",
            subtitle = "Warm C major ascending wooden chime",
            category = AlarmSoundCategory.BOTANICAL,
            notes = listOf(
                ChimeNote(523.25, 220, 45, 0.30),
                ChimeNote(659.25, 220, 45, 0.28),
                ChimeNote(783.99, 240, 45, 0.25),
                ChimeNote(987.77, 260, 45, 0.22),
                ChimeNote(1046.50, 520, 120, 0.20)
            )
        ),
        AlarmSoundPreset(
            id = SOUND_ZEN_BOWL,
            title = SOUND_ZEN_BOWL,
            subtitle = "Deep Tibetan singing bowl resonance",
            category = AlarmSoundCategory.BOTANICAL,
            notes = listOf(
                ChimeNote(220.0, 950, 140, 0.45, modulationHz = 2.5),
                ChimeNote(329.63, 750, 120, 0.38, modulationHz = 2.0),
                ChimeNote(220.0, 1100, 180, 0.45, modulationHz = 2.2)
            ),
            loopPauseMs = 1100L
        ),
        AlarmSoundPreset(
            id = "Morning Dew Harp",
            title = "Morning Dew Harp",
            subtitle = "Delicate sunrise harp arpeggio",
            category = AlarmSoundCategory.BOTANICAL,
            notes = listOf(
                ChimeNote(587.33, 180, 30, 0.22),
                ChimeNote(739.99, 180, 30, 0.22),
                ChimeNote(880.00, 200, 30, 0.22),
                ChimeNote(1174.66, 220, 35, 0.20),
                ChimeNote(1479.98, 480, 110, 0.18)
            )
        ),
        AlarmSoundPreset(
            id = "Soft Sage Marimba",
            title = "Soft Sage Marimba",
            subtitle = "Grounded acoustic wood cadence",
            category = AlarmSoundCategory.BOTANICAL,
            notes = listOf(
                ChimeNote(392.00, 170, 50, 0.18),
                ChimeNote(493.88, 170, 50, 0.18),
                ChimeNote(587.33, 180, 50, 0.18),
                ChimeNote(783.99, 260, 70, 0.16),
                ChimeNote(659.25, 340, 90, 0.16)
            )
        ),
        AlarmSoundPreset(
            id = "Nordic Sunrise Piano",
            title = "Nordic Sunrise Piano",
            subtitle = "Calm Nordic major seventh chords",
            category = AlarmSoundCategory.BOTANICAL,
            notes = listOf(
                ChimeNote(349.23, 280, 40, 0.34),
                ChimeNote(440.00, 260, 40, 0.32),
                ChimeNote(523.25, 280, 40, 0.30),
                ChimeNote(659.25, 300, 40, 0.26),
                ChimeNote(783.99, 560, 120, 0.22)
            )
        ),
        AlarmSoundPreset(
            id = "Crystal Brook",
            title = "Crystal Brook",
            subtitle = "Sparkling water-drop pentatonic cascade",
            category = AlarmSoundCategory.BOTANICAL,
            notes = listOf(
                ChimeNote(880.00, 140, 40, 0.16),
                ChimeNote(1046.50, 140, 40, 0.16),
                ChimeNote(1174.66, 150, 40, 0.15),
                ChimeNote(1318.51, 160, 45, 0.14),
                ChimeNote(1567.98, 420, 100, 0.14)
            )
        ),

        // Warm Studio Chimes
        AlarmSoundPreset(
            id = "Warm Amber Bells",
            title = "Warm Amber Bells",
            subtitle = "Golden brass bell intervals",
            category = AlarmSoundCategory.STUDIO_CHIMES,
            notes = listOf(
                ChimeNote(659.25, 320, 70, 0.36),
                ChimeNote(987.77, 300, 65, 0.34),
                ChimeNote(830.61, 280, 60, 0.30),
                ChimeNote(1318.51, 620, 130, 0.28)
            )
        ),
        AlarmSoundPreset(
            id = "Cascade Flutes",
            title = "Cascade Flutes",
            subtitle = "Soft legato thirds for unhurried reminders",
            category = AlarmSoundCategory.STUDIO_CHIMES,
            notes = listOf(
                ChimeNote(523.25, 240, 30, 0.12),
                ChimeNote(587.33, 240, 30, 0.12),
                ChimeNote(659.25, 260, 30, 0.12),
                ChimeNote(783.99, 280, 35, 0.12),
                ChimeNote(880.00, 520, 100, 0.12)
            )
        ),
        AlarmSoundPreset(
            id = "Soft Twilight Chime",
            title = "Soft Twilight Chime",
            subtitle = "Gentle descending evening melody",
            category = AlarmSoundCategory.STUDIO_CHIMES,
            notes = listOf(
                ChimeNote(783.99, 260, 55, 0.24),
                ChimeNote(659.25, 260, 55, 0.24),
                ChimeNote(587.33, 260, 55, 0.24),
                ChimeNote(493.88, 280, 60, 0.24),
                ChimeNote(392.00, 560, 120, 0.22)
            )
        ),
        AlarmSoundPreset(
            id = "Botanical Wind Chimes",
            title = "Botanical Wind Chimes",
            subtitle = "Airy high-overtone garden chime pattern",
            category = AlarmSoundCategory.STUDIO_CHIMES,
            notes = listOf(
                ChimeNote(987.77, 190, 45, 0.32),
                ChimeNote(1318.51, 190, 45, 0.30),
                ChimeNote(1479.98, 210, 50, 0.28),
                ChimeNote(987.77, 190, 45, 0.30),
                ChimeNote(1174.66, 460, 100, 0.26)
            )
        ),
        AlarmSoundPreset(
            id = "Evening Hearth",
            title = "Evening Hearth",
            subtitle = "Mellow low-mid cello & chime blend",
            category = AlarmSoundCategory.STUDIO_CHIMES,
            notes = listOf(
                ChimeNote(293.66, 360, 60, 0.32),
                ChimeNote(440.00, 320, 60, 0.28),
                ChimeNote(587.33, 320, 60, 0.25),
                ChimeNote(739.99, 540, 120, 0.22)
            )
        ),

        // Clear & Classic Alerts
        AlarmSoundPreset(
            id = "Gentle Pulse Beacon",
            title = "Gentle Pulse Beacon",
            subtitle = "Rhythmic double sonar pulse",
            category = AlarmSoundCategory.ALERTS,
            notes = listOf(
                ChimeNote(880.00, 180, 75, 0.20),
                ChimeNote(1046.50, 220, 110, 0.20),
                ChimeNote(880.00, 180, 75, 0.20),
                ChimeNote(1046.50, 300, 120, 0.20)
            ),
            loopPauseMs = 750L
        ),
        AlarmSoundPreset(
            id = "Clear Bell Trio",
            title = "Clear Bell Trio",
            subtitle = "Crisp studio desk bell triad",
            category = AlarmSoundCategory.ALERTS,
            notes = listOf(
                ChimeNote(1318.51, 160, 65, 0.36),
                ChimeNote(1567.98, 160, 65, 0.34),
                ChimeNote(1975.53, 480, 120, 0.30)
            ),
            loopPauseMs = 800L
        ),
        AlarmSoundPreset(
            id = SOUND_DIGITAL_BEEP,
            title = SOUND_DIGITAL_BEEP,
            subtitle = "Crisp twin-tone quartz studio beep",
            category = AlarmSoundCategory.ALERTS,
            notes = listOf(
                ChimeNote(1567.98, 120, 65, 0.28),
                ChimeNote(1567.98, 120, 140, 0.28),
                ChimeNote(2093.00, 200, 120, 0.26)
            ),
            loopPauseMs = 680L
        ),
        AlarmSoundPreset(
            id = "Classic Digital Alarm",
            title = "Classic Digital Alarm",
            subtitle = "Unmistakable 4-beat bedside clock chime",
            category = AlarmSoundCategory.ALERTS,
            notes = listOf(
                ChimeNote(2048.0, 110, 70, 0.42),
                ChimeNote(2048.0, 110, 70, 0.42),
                ChimeNote(2048.0, 110, 70, 0.42),
                ChimeNote(2048.0, 150, 180, 0.42)
            ),
            loopPauseMs = 520L
        ),
        AlarmSoundPreset(
            id = "Vibrant Wake Siren",
            title = "Vibrant Wake Siren",
            subtitle = "High-visibility alternating alert for urgent tasks",
            category = AlarmSoundCategory.ALERTS,
            notes = listOf(
                ChimeNote(987.77, 220, 25, 0.45),
                ChimeNote(1396.91, 220, 25, 0.45),
                ChimeNote(987.77, 220, 25, 0.45),
                ChimeNote(1396.91, 260, 80, 0.45)
            ),
            loopPauseMs = 450L
        )
    )

    fun presetsByCategory(category: AlarmSoundCategory): List<AlarmSoundPreset> {
        return presets.filter { it.category == category }
    }

    fun presetFor(rawSound: String): AlarmSoundPreset? {
        return presets.firstOrNull { it.id.equals(rawSound, ignoreCase = true) }
    }

    fun isCustomRingtone(rawSound: String): Boolean {
        return rawSound.startsWith(CUSTOM_RINGTONE_PREFIX)
    }

    fun encodeCustomRingtone(title: String, uriString: String): String {
        val cleanTitle = title.trim().ifBlank { "Device Ringtone" }
        val encodedTitle = runCatching { URLEncoder.encode(cleanTitle, "UTF-8") }.getOrDefault(cleanTitle)
        return "$CUSTOM_RINGTONE_PREFIX$encodedTitle|$uriString"
    }

    fun extractRingtoneTitle(rawSound: String): String {
        if (!isCustomRingtone(rawSound)) return rawSound
        val payload = rawSound.removePrefix(CUSTOM_RINGTONE_PREFIX)
        val rawTitle = payload.substringBefore("|", "Device Ringtone")
        return runCatching { URLDecoder.decode(rawTitle, "UTF-8") }
            .getOrDefault(rawTitle)
            .ifBlank { "Device Ringtone" }
    }

    fun extractRingtoneUri(rawSound: String): String? {
        if (!isCustomRingtone(rawSound)) return null
        val payload = rawSound.removePrefix(CUSTOM_RINGTONE_PREFIX)
        val uriPart = payload.substringAfter("|", "")
        return uriPart.takeIf { it.isNotBlank() }
    }

    fun displayName(rawSound: String): String {
        return when {
            rawSound.isBlank() -> SOUND_APP_DEFAULT
            isCustomRingtone(rawSound) -> extractRingtoneTitle(rawSound)
            else -> rawSound
        }
    }
}

object AlarmSoundEngine {
    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var activePreviewKey by mutableStateOf<String?>(null)
        private set
    var isAlarmLooping by mutableStateOf(false)
        private set
    var isAlarmMuted by mutableStateOf(false)
        private set

    private var previewJob: Job? = null
    private var alarmJob: Job? = null
    private var vibrationJob: Job? = null

    private var activeTrack: AudioTrack? = null
    private var activeRingtone: Ringtone? = null
    private var activeVoicePlayer: MediaPlayer? = null

    fun previewSound(
        context: Context,
        soundSpec: String,
        taskAudioPath: String? = null
    ) {
        if (activePreviewKey == soundSpec) {
            stopPreview()
            return
        }
        stopAll()
        activePreviewKey = soundSpec
        previewJob = scope.launch {
            try {
                playSinglePassOrTimed(
                    context = context.applicationContext,
                    soundSpec = soundSpec,
                    taskAudioPath = taskAudioPath,
                    maxDurationMs = 4200L
                )
            } finally {
                if (activePreviewKey == soundSpec) {
                    activePreviewKey = null
                }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        activePreviewKey = null
        releaseMediaHandles()
    }

    fun startAlarmLoop(
        context: Context,
        soundSpec: String,
        taskAudioPath: String?,
        enableVibration: Boolean
    ) {
        stopAll()
        isAlarmLooping = true
        isAlarmMuted = false

        val appCtx = context.applicationContext
        if (enableVibration) {
            startAlarmVibrationLoop(appCtx)
        }

        launchAlarmAudioLoop(appCtx, soundSpec, taskAudioPath)
    }

    fun setAlarmMuted(
        context: Context,
        muted: Boolean,
        soundSpec: String,
        taskAudioPath: String?
    ) {
        if (!isAlarmLooping) return
        isAlarmMuted = muted
        if (muted) {
            alarmJob?.cancel()
            alarmJob = null
            releaseMediaHandles()
        } else {
            launchAlarmAudioLoop(context.applicationContext, soundSpec, taskAudioPath)
        }
    }

    fun stopAll() {
        previewJob?.cancel()
        previewJob = null
        alarmJob?.cancel()
        alarmJob = null
        stopVibrationLoop()
        activePreviewKey = null
        isAlarmLooping = false
        isAlarmMuted = false
        releaseMediaHandles()
    }

    fun triggerHapticCue(context: Context, strong: Boolean = false) {
        try {
            val vibrator = getVibrator(context.applicationContext) ?: return
            if (!vibrator.hasVibrator()) return
            val durationMs = if (strong) 75L else 38L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (strong) 210 else 130
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Throwable) {
        }
    }

    private fun launchAlarmAudioLoop(
        appContext: Context,
        soundSpec: String,
        taskAudioPath: String?
    ) {
        alarmJob?.cancel()
        alarmJob = scope.launch {
            val resolvedPreset = AlarmSoundCatalog.presetFor(soundSpec)
            when {
                soundSpec == AlarmSoundCatalog.SOUND_SAVED_AUDIO && !taskAudioPath.isNullOrBlank() && File(taskAudioPath).exists() -> {
                    playLoopingVoiceFile(appContext, taskAudioPath)
                }
                AlarmSoundCatalog.isCustomRingtone(soundSpec) || soundSpec == AlarmSoundCatalog.SOUND_APP_DEFAULT -> {
                    val uri = resolveRingtoneUri(soundSpec)
                    playLoopingRingtone(appContext, uri)
                }
                resolvedPreset != null -> {
                    while (isActive && isAlarmLooping && !isAlarmMuted) {
                        playChimeSequence(resolvedPreset.notes)
                        if (isActive && isAlarmLooping && !isAlarmMuted) {
                            delay(resolvedPreset.loopPauseMs)
                        }
                    }
                }
                else -> {
                    val fallbackPreset = AlarmSoundCatalog.presets.first()
                    while (isActive && isAlarmLooping && !isAlarmMuted) {
                        playChimeSequence(fallbackPreset.notes)
                        if (isActive && isAlarmLooping && !isAlarmMuted) {
                            delay(fallbackPreset.loopPauseMs)
                        }
                    }
                }
            }
        }
    }

    private suspend fun playSinglePassOrTimed(
        context: Context,
        soundSpec: String,
        taskAudioPath: String?,
        maxDurationMs: Long
    ) {
        val preset = AlarmSoundCatalog.presetFor(soundSpec)
        when {
            soundSpec == AlarmSoundCatalog.SOUND_SAVED_AUDIO && !taskAudioPath.isNullOrBlank() && File(taskAudioPath).exists() -> {
                playTimedVoicePreview(taskAudioPath, maxDurationMs)
            }
            AlarmSoundCatalog.isCustomRingtone(soundSpec) || soundSpec == AlarmSoundCatalog.SOUND_APP_DEFAULT -> {
                val uri = resolveRingtoneUri(soundSpec)
                playTimedRingtonePreview(context, uri, maxDurationMs)
            }
            preset != null -> {
                playChimeSequence(preset.notes)
            }
            else -> {
                playChimeSequence(AlarmSoundCatalog.presets.first().notes)
            }
        }
    }

    private fun resolveRingtoneUri(soundSpec: String): Uri? {
        val customUriStr = AlarmSoundCatalog.extractRingtoneUri(soundSpec)
        if (!customUriStr.isNullOrBlank()) {
            return runCatching { Uri.parse(customUriStr) }.getOrNull()
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    }

    private suspend fun playTimedRingtonePreview(context: Context, uri: Uri?, maxDurationMs: Long) {
        if (uri == null) {
            playChimeSequence(AlarmSoundCatalog.presets.first().notes)
            return
        }
        val started = runCatching {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return@runCatching false
            activeRingtone = ringtone
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
            true
        }.getOrDefault(false)

        if (!started) {
            playChimeSequence(AlarmSoundCatalog.presets.first().notes)
            return
        }
        try {
            var elapsed = 0L
            while (elapsed < maxDurationMs && activePreviewKey != null) {
                delay(150L)
                elapsed += 150L
            }
        } finally {
            runCatching { activeRingtone?.stop() }
            activeRingtone = null
        }
    }

    private suspend fun playLoopingRingtone(context: Context, uri: Uri?) {
        if (uri == null) {
            val fallback = AlarmSoundCatalog.presets.first()
            while (isAlarmLooping && !isAlarmMuted) {
                playChimeSequence(fallback.notes)
                delay(fallback.loopPauseMs)
            }
            return
        }

        val ringtone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()
        if (ringtone == null) {
            val fallback = AlarmSoundCatalog.presets.first()
            while (isAlarmLooping && !isAlarmMuted) {
                playChimeSequence(fallback.notes)
                delay(fallback.loopPauseMs)
            }
            return
        }

        activeRingtone = ringtone
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.play()
        }

        try {
            while (isAlarmLooping && !isAlarmMuted) {
                delay(450L)
                if (isAlarmLooping && !isAlarmMuted && !ringtone.isPlaying) {
                    runCatching { ringtone.play() }
                }
            }
        } finally {
            runCatching { ringtone.stop() }
            if (activeRingtone === ringtone) {
                activeRingtone = null
            }
        }
    }

    private suspend fun playTimedVoicePreview(path: String, maxDurationMs: Long) {
        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
            }
        }.getOrNull() ?: return

        activeVoicePlayer = player
        try {
            var elapsed = 0L
            while (elapsed < maxDurationMs && activePreviewKey != null && player.isPlaying) {
                delay(120L)
                elapsed += 120L
            }
        } finally {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
            if (activeVoicePlayer === player) {
                activeVoicePlayer = null
            }
        }
    }

    private suspend fun playLoopingVoiceFile(context: Context, path: String) {
        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        if (player == null) {
            val fallback = AlarmSoundCatalog.presets.first()
            while (isAlarmLooping && !isAlarmMuted) {
                playChimeSequence(fallback.notes)
                delay(fallback.loopPauseMs)
            }
            return
        }

        activeVoicePlayer = player
        try {
            while (isAlarmLooping && !isAlarmMuted) {
                delay(350L)
            }
        } finally {
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
            if (activeVoicePlayer === player) {
                activeVoicePlayer = null
            }
        }
    }

    private suspend fun playChimeSequence(notes: List<ChimeNote>) {
        if (notes.isEmpty()) return
        val pcm = synthesizeChimePcm(notes)
        if (pcm.isEmpty()) return

        val byteCount = pcm.size * 2
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(byteCount)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        activeTrack = track
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val expectedDurationMs = notes.sumOf { it.durationMs + it.gapAfterMs }.toLong()
            var waited = 0L
            while (waited < expectedDurationMs && (activePreviewKey != null || (isAlarmLooping && !isAlarmMuted))) {
                delay(60L)
                waited += 60L
            }
        } finally {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
            if (activeTrack === track) {
                activeTrack = null
            }
        }
    }

    internal fun synthesizeChimePcm(notes: List<ChimeNote>): ShortArray {
        val totalSamples = notes.sumOf { ((it.durationMs + it.gapAfterMs) * SAMPLE_RATE) / 1000 }
        if (totalSamples <= 0) return ShortArray(0)

        val buffer = ShortArray(totalSamples)
        var offset = 0

        for (note in notes) {
            val noteSamples = (note.durationMs * SAMPLE_RATE) / 1000
            val gapSamples = (note.gapAfterMs * SAMPLE_RATE) / 1000
            val attackSamples = (noteSamples * 0.08).toInt().coerceAtLeast(1)

            for (i in 0 until noteSamples) {
                if (offset + i >= totalSamples) break
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / noteSamples.coerceAtLeast(1)

                // Smooth attack + natural exponential decay envelope
                val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
                val decay = kotlin.math.exp(-3.4 * progress)
                val vibrato = if (note.modulationHz > 0.0) {
                    1.0 + 0.14 * sin(2.0 * PI * note.modulationHz * t)
                } else 1.0

                val fundamental = sin(2.0 * PI * note.freqHz * t)
                val secondHarmonic = sin(2.0 * PI * (note.freqHz * 2.0) * t) * note.harmonicWeight
                val thirdHarmonic = sin(2.0 * PI * (note.freqHz * 3.0) * t) * (note.harmonicWeight * 0.35)

                val sampleVal = ((fundamental + secondHarmonic + thirdHarmonic) * attack * decay * vibrato)
                    .coerceIn(-1.0, 1.0)
                buffer[offset + i] = (sampleVal * Short.MAX_VALUE * 0.65).toInt().toShort()
            }
            offset += noteSamples + gapSamples
        }
        return buffer
    }

    internal fun encodePcmToWavBytes(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataSize = pcm.size * 2
        val byteRate = sampleRate * 2 // mono 16-bit
        val totalChunkSize = 36 + dataSize
        val out = ByteArrayOutputStream(44 + dataSize)

        fun writeAscii(s: String) {
            s.forEach { out.write(it.code) }
        }
        fun writeIntLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 24) and 0xFF)
        }
        fun writeShortLE(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }

        writeAscii("RIFF")
        writeIntLE(totalChunkSize)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeIntLE(16) // PCM subchunk size
        writeShortLE(1) // PCM format
        writeShortLE(1) // Mono
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(2) // Block align
        writeShortLE(16) // Bits per sample
        writeAscii("data")
        writeIntLE(dataSize)
        for (sample in pcm) {
            val s = sample.toInt()
            out.write(s and 0xFF)
            out.write((s shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    fun synthesizeChimeWavFile(context: Context, preset: AlarmSoundPreset): File? {
        return runCatching {
            val dir = StorageAndBackupCoordinator.alarmTonesCacheDir(context.applicationContext)
            val slug = preset.id
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "chime" }
            val wavFile = File(dir, "$slug.wav")
            if (wavFile.exists() && wavFile.length() > 44L) {
                return@runCatching wavFile
            }
            val pcm = synthesizeChimePcm(preset.notes)
            if (pcm.isEmpty()) return@runCatching null
            val bytes = encodePcmToWavBytes(pcm)
            wavFile.writeBytes(bytes)
            wavFile
        }.getOrNull()
    }

    private var activeVibrator: Vibrator? = null

    private fun startAlarmVibrationLoop(context: Context) {
        stopVibrationLoop()
        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        activeVibrator = vibrator

        vibrationJob = scope.launch {
            val pattern = longArrayOf(0L, 280L, 220L, 280L, 900L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                }
                while (isActive && isAlarmLooping) {
                    delay(500L)
                }
            } else {
                while (isActive && isAlarmLooping) {
                    @Suppress("DEPRECATION")
                    runCatching { vibrator.vibrate(320L) }
                    delay(1100L)
                }
            }
        }
    }

    private fun stopVibrationLoop() {
        vibrationJob?.cancel()
        vibrationJob = null
        runCatching { activeVibrator?.cancel() }
        activeVibrator = null
    }

    private fun releaseMediaHandles() {
        runCatching {
            activeTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        }
        activeTrack = null

        runCatching { activeRingtone?.stop() }
        activeRingtone = null

        runCatching {
            activeVoicePlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        activeVoicePlayer = null
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }
}
