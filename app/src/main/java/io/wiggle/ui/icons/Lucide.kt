package io.wiggle.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lucide icons kept as their original 24x24 SVG path data and stroked at draw time. Parsing the
 * path is cheaper to maintain than hand-porting each one into an ImageVector builder, and the
 * geometry stays identical to upstream.
 */
@Immutable
data class LucideIcon(val paths: List<String>) {
    constructor(vararg d: String) : this(d.toList())
}

@Composable
fun Icon(
    icon: LucideIcon,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Color.White,
    strokeWidth: Float = 2f,
    contentDescription: String? = null,
) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        val factor = this.size.minDimension / 24f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(factor, pivot = Offset.Zero) {
            paths.forEach { drawPath(it, tint, style = stroke) }
        }
    }
}

/** Lucide 24x24 path data. */
object Lucide {
    val Plus = LucideIcon("M5 12h14", "M12 5v14")
    val Minus = LucideIcon("M5 12h14")
    val X = LucideIcon("M18 6 6 18", "M6 6l12 12")
    val Check = LucideIcon("M20 6 9 17l-5-5")
    val ChevronRight = LucideIcon("M9 18l6-6-6-6")
    val ChevronLeft = LucideIcon("M15 18l-6-6 6-6")
    val ChevronDown = LucideIcon("M6 9l6 6 6-6")
    val ChevronUp = LucideIcon("M18 15l-6-6-6 6")
    val ArrowDown = LucideIcon("M12 5v14", "M19 12l-7 7-7-7")
    val ArrowUp = LucideIcon("M12 19V5", "M5 12l7-7 7 7")
    val ArrowRight = LucideIcon("M5 12h14", "M12 5l7 7-7 7")

    val House = LucideIcon(
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )
    val ChartLine = LucideIcon("M3 3v16a2 2 0 0 0 2 2h16", "M7 15l4-4 3 3 5-6")
    val Scale = LucideIcon("M3 3h18v18H3z", "M8 11a4 4 0 0 1 8 0", "M12 11l1.6-2.4")
    val TrendingUp = LucideIcon("M3 17l5-5 4 3 8-8", "M15 7h5v5")
    val Ruler = LucideIcon("M2 8h20v8H2z", "M6 8v3", "M10 8v4", "M14 8v3", "M18 8v4")
    val Droplet = LucideIcon("M12 3s-6 6.5-6 11a6 6 0 0 0 12 0c0-4.5-6-11-6-11z")
    val Bell = LucideIcon("M6 9a6 6 0 0 1 12 0c0 5 2 7 2 7H4s2-2 2-7", "M10 20a2 2 0 0 0 4 0")
    val BellOff = LucideIcon(
        "M8.7 3.3A6 6 0 0 1 18 9c0 5 2 7 2 7H7",
        "M10 20a2 2 0 0 0 4 0",
        "M2 2l20 20",
    )

    val Calendar = LucideIcon("M3 5h18v16H3z", "M3 10h18", "M8 3v4", "M16 3v4")
    val Clock = LucideIcon("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 7v5l3 2")
    val Target = LucideIcon(
        "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z",
        "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z",
    )
    val Flame = LucideIcon("M12 3c3 4 6 6 6 10a6 6 0 0 1-12 0c0-2 1-3 2-4 .5 1.5 1.5 2 2 2 0-3 1-6 2-8z")

    val User = LucideIcon("M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M4 21a8 8 0 0 1 16 0")
    val Users = LucideIcon(
        "M10 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z",
        "M2 21a8 8 0 0 1 16 0",
        "M17 5a3.5 3.5 0 0 1 0 7",
        "M19.5 21a6 6 0 0 0-3-5",
    )
    val UserPlus = LucideIcon(
        "M10 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z",
        "M2 21a8 8 0 0 1 16 0",
        "M18 8v6",
        "M15 11h6",
    )
    // Upstream Lucide `settings`. The hand-drawn octagon this replaced lost its teeth at tab-bar
    // size and read as a smudge; the real outline keeps its shape down to 22dp.
    val Settings = LucideIcon(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 " +
            "0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 " +
            "2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 " +
            "2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 " +
            "2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 " +
            "2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 " +
            "2 0 0 0-2-2z",
        "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    )
    val Trash = LucideIcon("M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6", "M10 11v6", "M14 11v6")
    val Undo = LucideIcon("M3 7v6h6", "M3.5 13a9 9 0 1 0 2.1-9.4L3 7")
    val Share = LucideIcon(
        "M12 3v13",
        "M8 7l4-4 4 4",
        "M5 15v4a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-4",
    )
    val Download = LucideIcon("M12 3v13", "M8 12l4 4 4-4", "M5 19h14")
    val Moon = LucideIcon("M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5z")
    val Sun = LucideIcon(
        "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z",
        "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4",
        "M2 12h2", "M20 12h2", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4",
    )
    val Info = LucideIcon("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 11v5", "M12 8h.01")
    val Pause = LucideIcon("M9 4v16", "M15 4v16")
    val Play = LucideIcon("M7 4l12 8-12 8z")
    val Heart = LucideIcon("M12 20s-7-4.6-7-9.5A4.5 4.5 0 0 1 12 7a4.5 4.5 0 0 1 7 3.5c0 4.9-7 9.5-7 9.5z")
    val Zap = LucideIcon("M13 2L4 14h7l-1 8 9-12h-7l1-8z")
    val Sparkles = LucideIcon(
        "M12 3l1.6 4.4L18 9l-4.4 1.6L12 15l-1.6-4.4L6 9l4.4-1.6z",
        "M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z",
    )
    val Pencil = LucideIcon("M4 20h4L20 8l-4-4L4 16z", "M14 6l4 4")
    val Repeat = LucideIcon("M17 2l4 4-4 4", "M3 11V9a4 4 0 0 1 4-4h14", "M7 22l-4-4 4-4", "M21 13v2a4 4 0 0 1-4 4H3")
    val Search = LucideIcon("M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14z", "M20 20l-4-4")
}
