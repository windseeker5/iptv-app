package com.kdresdell.iptvtv.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

// Poster sizes: 2:3 for movie/series art, square for channel logos (which
// are usually wide/transparent PNGs - Fit keeps them whole).
val PosterSize = DpSize(74.dp, 110.dp)
val LogoSize = DpSize(62.dp, 62.dp)

// One saved item in My TV / My Library: poster, title, category, and a
// short description. The whole row plays/opens; the trash icon removes it.
@Composable
fun MediaRow(
    imageUrl: String,
    title: String,
    category: String,
    description: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    imageSize: DpSize = PosterSize,
    imageIsLogo: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Poster(imageUrl = imageUrl, fallbackText = title, size = imageSize, isLogo = imageIsLogo)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (category.isNotBlank()) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Small grey trash icon rather than a "Remove" text button - takes
        // less room and reads as an action. The screens offer Undo, since
        // a small icon is easy to hit by accident.
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Poster(imageUrl: String, fallbackText: String, size: DpSize, isLogo: Boolean) {
    // Blank URL, or one that fails to load, shows the title's first letter
    // instead of an empty box.
    val fallback: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = TitleFormat.clean(fallbackText).take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (imageUrl.isBlank()) {
            fallback()
        } else if (isLogo) {
            // Channel logos are mostly opaque rectangles (often on white)
            // of any aspect ratio, letterboxed inside the tile - so the
            // rounding has to follow the logo's own edges, not the tile's.
            // Sizing the Image to the logo's aspect ratio lets clip() do that.
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                error = { fallback() },
                success = { state ->
                    val logo = state.painter
                    val intrinsic = logo.intrinsicSize
                    val ratio = if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                        intrinsic.width / intrinsic.height
                    } else {
                        1f
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = logo,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .aspectRatio(ratio)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            )
        } else {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { fallback() }
            )
        }
    }
}
