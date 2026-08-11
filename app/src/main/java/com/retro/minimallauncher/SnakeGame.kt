package com.retro.minimallauncher

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val BOARD_COLUMNS = 18
private const val BOARD_ROWS = 24
private const val SNAKE_HIGH_SCORE_KEY = "snake_high_score"
private const val SNAKE_SOUND_KEY = "snake_sound"

private data class SnakeCell(val x: Int, val y: Int)

private enum class SnakeDirection { UP, DOWN, LEFT, RIGHT }
private enum class SnakePhase { READY, COUNTDOWN, PLAYING, LEVEL_UP, PAUSED, GAME_OVER }

private class SnakeGameModel(initialHighScore: Int) {
    var snake by mutableStateOf(defaultSnake())
    var direction by mutableStateOf(SnakeDirection.RIGHT)
    var queuedDirection by mutableStateOf(SnakeDirection.RIGHT)
    var food by mutableStateOf(SnakeCell(13, 12))
    var walls by mutableStateOf<List<SnakeCell>>(emptyList())
    var phase by mutableStateOf(SnakePhase.READY)
    var score by mutableIntStateOf(0)
    var highScore by mutableIntStateOf(initialHighScore)
    var level by mutableIntStateOf(1)
    var countdown by mutableIntStateOf(3)

    fun newGame() {
        snake = defaultSnake()
        direction = SnakeDirection.RIGHT
        queuedDirection = SnakeDirection.RIGHT
        walls = emptyList()
        score = 0
        level = 1
        food = findFood()
        countdown = 3
        phase = SnakePhase.COUNTDOWN
    }

    fun pause() {
        if (phase == SnakePhase.PLAYING || phase == SnakePhase.COUNTDOWN || phase == SnakePhase.LEVEL_UP) {
            phase = SnakePhase.PAUSED
        }
    }

    fun resume() {
        if (phase == SnakePhase.PAUSED) {
            countdown = 3
            phase = SnakePhase.COUNTDOWN
        }
    }

    fun togglePause() {
        when (phase) {
            SnakePhase.PLAYING -> pause()
            SnakePhase.PAUSED -> resume()
            SnakePhase.READY, SnakePhase.GAME_OVER -> newGame()
            SnakePhase.COUNTDOWN, SnakePhase.LEVEL_UP -> Unit
        }
    }

    fun setDirection(next: SnakeDirection) {
        if (phase != SnakePhase.PLAYING && phase != SnakePhase.COUNTDOWN) return
        if (!isOpposite(direction, next)) queuedDirection = next
    }

    fun speedMs(): Long = (220 - score * 4).coerceAtLeast(95).toLong()

    fun step(onFood: () -> Unit, onHighScore: (Int) -> Unit) {
        if (phase != SnakePhase.PLAYING) return

        if (!isOpposite(direction, queuedDirection)) direction = queuedDirection
        val head = snake.first()
        val nextHead = when (direction) {
            SnakeDirection.UP -> SnakeCell(head.x, head.y - 1)
            SnakeDirection.DOWN -> SnakeCell(head.x, head.y + 1)
            SnakeDirection.LEFT -> SnakeCell(head.x - 1, head.y)
            SnakeDirection.RIGHT -> SnakeCell(head.x + 1, head.y)
        }

        val ateFood = nextHead == food
        val bodyCollisionSet = if (ateFood) snake else snake.dropLast(1)
        val hitBoundary = nextHead.x !in 0 until BOARD_COLUMNS || nextHead.y !in 0 until BOARD_ROWS
        val hitSelf = nextHead in bodyCollisionSet
        val hitWall = nextHead in walls

        if (hitBoundary || hitSelf || hitWall) {
            phase = SnakePhase.GAME_OVER
            return
        }

        snake = if (ateFood) {
            listOf(nextHead) + snake
        } else {
            listOf(nextHead) + snake.dropLast(1)
        }

        if (!ateFood) return

        score += 1
        onFood()
        if (score > highScore) {
            highScore = score
            onHighScore(score)
        }

        val newLevel = when {
            score >= 30 -> 4
            score >= 20 -> 3
            score >= 10 -> 2
            else -> 1
        }

        if (newLevel != level) {
            level = newLevel
            walls = buildSafeWalls(newLevel, snake, food)
            food = findFood()
            phase = SnakePhase.LEVEL_UP
        } else {
            food = findFood()
        }
    }

    private fun findFood(): SnakeCell {
        val blocked = snake.toSet() + walls.toSet()
        val free = mutableListOf<SnakeCell>()
        for (y in 0 until BOARD_ROWS) {
            for (x in 0 until BOARD_COLUMNS) {
                val cell = SnakeCell(x, y)
                if (cell !in blocked) free += cell
            }
        }
        return if (free.isNotEmpty()) free[Random.nextInt(free.size)] else SnakeCell(0, 0)
    }

    private fun buildSafeWalls(
        targetLevel: Int,
        currentSnake: List<SnakeCell>,
        currentFood: SnakeCell
    ): List<SnakeCell> {
        if (targetLevel <= 1) return emptyList()

        val head = currentSnake.first()
        val blocked = currentSnake.toSet() + currentFood
        fun safe(segment: List<SnakeCell>): Boolean = segment.none { cell ->
            cell in blocked || abs(cell.x - head.x) + abs(cell.y - head.y) <= 3
        }

        val levelTwoCandidates = listOf(
            (4..8).map { SnakeCell(it, 7) },
            (9..13).map { SnakeCell(it, 16) },
            (7..11).map { SnakeCell(it, 10) },
            (5..9).map { SnakeCell(it, 18) }
        )
        val levelThreeCandidates = listOf(
            (9..15).map { SnakeCell(12, it) },
            (4..10).map { SnakeCell(5, it) },
            (6..12).map { SnakeCell(it, 15) },
            (8..14).map { SnakeCell(it, 6) }
        )
        val levelFourExtraCandidates = listOf(
            (3..7).map { SnakeCell(it, 19) },
            (10..14).map { SnakeCell(it, 4) },
            (12..17).map { SnakeCell(3, it) },
            (5..10).map { SnakeCell(15, it) }
        )

        val primaryPool = if (targetLevel == 2) levelTwoCandidates else levelThreeCandidates
        val primary = primaryPool.firstOrNull(::safe).orEmpty()
        if (targetLevel < 4) return primary

        val used = primary.toSet()
        val secondary = levelFourExtraCandidates.firstOrNull { candidate ->
            safe(candidate) && candidate.none { it in used }
        }.orEmpty()
        return primary + secondary
    }

    companion object {
        private fun defaultSnake() = listOf(
            SnakeCell(8, 12),
            SnakeCell(7, 12),
            SnakeCell(6, 12),
            SnakeCell(5, 12)
        )

        private fun isOpposite(a: SnakeDirection, b: SnakeDirection): Boolean =
            (a == SnakeDirection.UP && b == SnakeDirection.DOWN) ||
                (a == SnakeDirection.DOWN && b == SnakeDirection.UP) ||
                (a == SnakeDirection.LEFT && b == SnakeDirection.RIGHT) ||
                (a == SnakeDirection.RIGHT && b == SnakeDirection.LEFT)
    }
}

@Composable
internal fun SnakeScreen(windowFocused: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("retro_launcher_prefs", android.content.Context.MODE_PRIVATE) }
    val model = remember { SnakeGameModel(prefs.getInt(SNAKE_HIGH_SCORE_KEY, 0)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean(SNAKE_SOUND_KEY, false)) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }
    val activity = context as? ComponentActivity

    DisposableEffect(toneGenerator) {
        onDispose { toneGenerator.release() }
    }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) model.pause()
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            activity.lifecycle.removeObserver(observer)
            activity.requestedOrientation = previousOrientation
        }
    }

    LaunchedEffect(windowFocused) {
        if (!windowFocused) model.pause()
    }

    LaunchedEffect(model.phase) {
        when (model.phase) {
            SnakePhase.COUNTDOWN -> {
                for (value in 3 downTo 1) {
                    if (model.phase != SnakePhase.COUNTDOWN) return@LaunchedEffect
                    model.countdown = value
                    delay(500)
                }
                if (model.phase == SnakePhase.COUNTDOWN) model.phase = SnakePhase.PLAYING
            }
            SnakePhase.LEVEL_UP -> {
                delay(750)
                if (model.phase == SnakePhase.LEVEL_UP) model.phase = SnakePhase.PLAYING
            }
            SnakePhase.PLAYING -> {
                while (model.phase == SnakePhase.PLAYING) {
                    delay(model.speedMs())
                    model.step(
                        onFood = {
                            if (soundEnabled) {
                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
                            }
                        },
                        onHighScore = { prefs.edit().putInt(SNAKE_HIGH_SCORE_KEY, it).apply() }
                    )
                }
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SNAKE",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                "Back",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onBack() }.padding(6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "SCORE ${model.score}",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                "HIGH ${model.highScore}",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                "LEVEL ${model.level}",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            SnakeBoard(model)

            when (model.phase) {
                SnakePhase.READY -> SnakeOverlay(
                    title = "READY",
                    subtitle = "● START",
                    onClick = { model.newGame() }
                )
                SnakePhase.COUNTDOWN -> SnakeOverlay(
                    title = model.countdown.toString(),
                    subtitle = "GET READY"
                )
                SnakePhase.LEVEL_UP -> SnakeOverlay(
                    title = "LEVEL ${model.level}",
                    subtitle = if (model.level == 2) "WALL ADDED" else "BOARD CHANGED"
                )
                SnakePhase.PAUSED -> SnakeOverlay(
                    title = "PAUSED",
                    subtitle = "● RESUME",
                    onClick = { model.resume() }
                )
                SnakePhase.GAME_OVER -> SnakeOverlay(
                    title = "GAME OVER",
                    subtitle = "SCORE ${model.score}  •  ● PLAY AGAIN",
                    onClick = { model.newGame() }
                )
                SnakePhase.PLAYING -> Unit
            }
        }

        Spacer(Modifier.height(7.dp))
        SnakeDirectionPad(
            onUp = { model.setDirection(SnakeDirection.UP) },
            onDown = { model.setDirection(SnakeDirection.DOWN) },
            onLeft = { model.setDirection(SnakeDirection.LEFT) },
            onRight = { model.setDirection(SnakeDirection.RIGHT) },
            onCenter = { model.togglePause() }
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SnakeButton(
                label = "SOUND ${if (soundEnabled) "ON" else "OFF"}",
                modifier = Modifier.weight(1f)
            ) {
                soundEnabled = !soundEnabled
                prefs.edit().putBoolean(SNAKE_SOUND_KEY, soundEnabled).apply()
            }
            SnakeButton("BACK", Modifier.weight(1f), onBack)
        }
        Text(
            "Center = Start/Pause  •  D-pad = Move",
            color = Color.White.copy(alpha = 0.58f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun SnakeBoard(model: SnakeGameModel) {
    val snake = model.snake
    val food = model.food
    val walls = model.walls

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(BOARD_COLUMNS.toFloat() / BOARD_ROWS.toFloat())
            .background(Color.Black)
            .border(2.dp, Color.White)
    ) {
        val cellWidth = size.width / BOARD_COLUMNS
        val cellHeight = size.height / BOARD_ROWS
        val insetX = cellWidth * 0.12f
        val insetY = cellHeight * 0.12f

        fun drawCell(cell: SnakeCell, color: Color, inset: Boolean = true) {
            drawRect(
                color = color,
                topLeft = Offset(
                    cell.x * cellWidth + if (inset) insetX else 0f,
                    cell.y * cellHeight + if (inset) insetY else 0f
                ),
                size = Size(
                    cellWidth - if (inset) insetX * 2 else 0f,
                    cellHeight - if (inset) insetY * 2 else 0f
                )
            )
        }

        walls.forEach { drawCell(it, Color.White, inset = false) }
        snake.forEachIndexed { index, cell ->
            drawCell(cell, Color.White, inset = index != 0)
        }

        drawCircle(
            color = Color.White,
            radius = minOf(cellWidth, cellHeight) * 0.31f,
            center = Offset(
                food.x * cellWidth + cellWidth / 2f,
                food.y * cellHeight + cellHeight / 2f
            )
        )
    }
}

@Composable
private fun SnakeOverlay(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(5.dp))
            .border(1.dp, Color.White, RoundedCornerShape(5.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SnakeDirectionPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SnakeDpadKey("▲", onUp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SnakeDpadKey("◀", onLeft)
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .padding(4.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(29.dp))
                    .clickable { onCenter() },
                contentAlignment = Alignment.Center
            ) {
                Text("●", color = Color.White, fontSize = 18.sp)
            }
            SnakeDpadKey("▶", onRight)
        }
        SnakeDpadKey("▼", onDown)
    }
}

@Composable
private fun SnakeDpadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .padding(2.dp)
            .border(1.dp, Color.White, RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 17.sp)
    }
}

@Composable
private fun SnakeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}
