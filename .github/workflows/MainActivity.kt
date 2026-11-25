package com.example.winampcarplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

data class Song(val title: String, val artist: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            WinampCarApp()
        }
    }
}

@Composable
fun WinampCarApp() {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            songs = loadAudioFiles(context)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            songs = loadAudioFiles(context)
        } else {
            launcher.launch(permission)
        }
    }

    WinampCarScreen(context = context, playlist = songs, onRefresh = {
        if (hasPermission) songs = loadAudioFiles(context) else launcher.launch(permission)
    })
}

fun loadAudioFiles(context: Context): List<Song> {
    val songList = mutableListOf<Song>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "Unknown Track"
            val artist = cursor.getString(artistCol) ?: "Unknown Artist"
            val contentUri = ContentUris.withAppendedId(collection, id)
            songList.add(Song(title, artist, contentUri))
        }
    }
    return songList
}

@Composable
fun WinampCarScreen(context: Context, playlist: List<Song>, onRefresh: () -> Unit) {
    val player = remember { ExoPlayer.Builder(context).build() }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(playlist) {
        if (playlist.isNotEmpty()) {
            val mediaItems = playlist.map { MediaItem.fromUri(it.uri) }
            player.setMediaItems(mediaItems)
            player.prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF191923))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(Color(0xFF232332))
                .border(2.dp, Color(0xFF4A4A5E))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WINAMP CAR EDITION v1.0",
                    color = Color(0xFFFFB000),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A4A))
                ) {
                    Text("SCAN USB/STORAGE", color = Color(0xFF00FF41), fontSize = 12.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Black)
                    .border(2.dp, Color(0xFF555566))
                    .padding(16.dp)
            ) {
                val currentSong = playlist.getOrNull(currentIndex)
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = currentSong?.title ?: "NO AUDIO FILES FOUND",
                        color = Color(0xFF00FF41),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSong?.artist ?: "Tap SCAN USB/STORAGE or add MP3/FLAC",
                        color = Color(0xFFFFB000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WinampControlButton("PREV") {
                    if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                }
                WinampControlButton(if (isPlaying) "PAUSE" else "PLAY") {
                    if (player.isPlaying) player.pause() else player.play()
                }
                WinampControlButton("STOP") {
                    player.stop()
                    player.prepare()
                }
                WinampControlButton("NEXT") {
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(Color.Black)
                .border(2.dp, Color(0xFF4A4A5E))
                .padding(12.dp)
        ) {
            Text(
                text = "PLAYLIST (${playlist.size} TRACKS)",
                color = Color(0xFFFFB000),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(playlist) { index, song ->
                    val isSelected = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) Color(0xFF003B00) else Color(0xFF111116))
                            .clickable {
                                currentIndex = index
                                player.seekTo(index, 0L)
                                player.play()
                            }
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "${index + 1}. ${song.title}",
                            color = if (isSelected) Color.White else Color(0xFF00FF41),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WinampControlButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .height(76.dp)
            .width(110.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A4A))
    ) {
        Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
