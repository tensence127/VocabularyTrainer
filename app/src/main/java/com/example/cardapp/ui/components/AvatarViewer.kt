package com.example.cardapp.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Полноэкранный просмотр аватарки на тёмном фоне: пинч-зум с панорамой,
 * меню «⋮» с сохранением в галерею. Закрывается тапом или жестом «назад».
 *
 * Сразу показывает миниатюру [thumbBase64]; параллельно [fetchFull] качает
 * полную версию (отдельный документ Firestore) и подменяет картинку, когда
 * она готова. У старых аватарок полной версии нет — остаётся миниатюра.
 */
@Composable
fun AvatarViewer(
    thumbBase64: String,
    fetchFull: ((String?) -> Unit) -> Unit = { it(null) },
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        fetchFull { result ->
            scope.launch(Dispatchers.Default) {
                val decoded = result?.takeIf { it.isNotBlank() }?.let(::decodeAvatarBitmap)
                withContext(Dispatchers.Main) {
                    if (decoded != null) fullBitmap = decoded
                    loading = false
                }
            }
        }
    }

    val thumbBitmap = remember(thumbBase64) { decodeAvatarBitmap(thumbBase64) }
    val bitmap = fullBitmap ?: thumbBitmap

    // Сохранение в галерею
    fun saveShownBitmap() {
        val shown = fullBitmap ?: thumbBitmap ?: return
        scope.launch(Dispatchers.IO) {
            val ok = saveToGallery(context, shown.asAndroidBitmap())
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (ok) "Сохранено в галерею" else "Не удалось сохранить",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val requestWrite = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveShownBitmap()
    }
    fun onSaveClick() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            requestWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveShownBitmap()
        }
    }

    var menuOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                // Без ripple — просто закрыть по тапу, как в галереях.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val cw = constraints.maxWidth.toFloat()
            val ch = constraints.maxHeight.toFloat()
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            fun clampOffset(o: Offset, s: Float, bmp: ImageBitmap): Offset {
                val aspect = bmp.width.toFloat() / bmp.height
                val drawnW = minOf(cw, ch * aspect)
                val drawnH = drawnW / aspect
                val maxX = ((drawnW * s - cw) / 2f).coerceAtLeast(0f)
                val maxY = ((drawnH * s - ch) / 2f).coerceAtLeast(0f)
                return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Аватарка",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                val ratio = newScale / scale
                                scale = newScale
                                offset = clampOffset(offset * ratio + pan, newScale, bitmap)
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }

            if (loading) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(48.dp),
                )
            }

            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Сохранить в галерею") },
                        onClick = {
                            menuOpen = false
                            onSaveClick()
                        },
                    )
                }
            }
        }
    }
}

/** Кладёт картинку в галерею (Pictures) через MediaStore; false — не вышло. */
private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean = try {
    val name = "avatar_${System.currentTimeMillis()}.jpg"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        false
    } else {
        val written = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        if (!written) resolver.delete(uri, null, null)
        written
    }
} catch (_: Exception) {
    false
}
