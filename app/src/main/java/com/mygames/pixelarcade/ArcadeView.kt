package com.mygames.pixelarcade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class ArcadeView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("pixel_arcade_scores", Context.MODE_PRIVATE)

    private val bg = Color.rgb(12, 16, 31)
    private val panel = Color.rgb(27, 33, 48)
    private val white = Color.WHITE
    private val muted = Color.rgb(171, 180, 200)
    private val red = Color.rgb(255, 112, 125)
    private val mint = Color.rgb(95, 232, 182)
    private val gold = Color.rgb(255, 196, 94)
    private val violet = Color.rgb(129, 118, 255)

    private val gameNames = listOf("Neon Drive", "Castle Guard", "Escape Files", "Final Whistle")
    private val accents = listOf(Color.rgb(96, 163, 255), mint, gold, red)
    private val descriptions = listOf(
        "Dodge traffic and survive the road.",
        "Tap enemies and protect the castle.",
        "Solve clues and escape the lock.",
        "Choose a corner and beat the keeper."
    )

    private var screen = 0 // 0 menu, 1 play, 2 game over
    private var game = 0
    private var score = 0
    private var endMessage = ""
    private var lastFrame = 0L

    // Car game
    private var lane = 1
    private var obstacleLane = 0
    private var obstacleY = -0.18f
    private var speed = 0.48f
    private var roadOffset = 0f

    // Tower game
    private data class Enemy(var progress: Float)
    private val enemies = mutableListOf<Enemy>()
    private var castleHp = 5
    private var wave = 1
    private var spawnTimer = 1.2f
    private var shieldUntil = 0L

    // Escape game
    private var clueIndex = 0
    private var hintVisible = false
    private val clues = listOf(
        "Which key opens the bronze door with the crescent moon mark?",
        "I have hands but cannot clap. What am I?",
        "What comes next in 2, 4, 8, 16?"
    )
    private val options = listOf(
        listOf("Sun key", "Moon key", "Star key"),
        listOf("A clock", "A river", "A statue"),
        listOf("24", "32", "18")
    )
    private val correct = listOf(1, 0, 1)

    // Penalty game
    private var attempts = 0
    private var goals = 0
    private var keeper = 1
    private var shotText = ""

    init {
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(bg)

        paint.color = Color.argb(90, 90, 108, 255)
        canvas.drawCircle(w * 0.90f, h * 0.08f, w * 0.42f, paint)
        canvas.drawCircle(w * 0.08f, h * 0.75f, w * 0.30f, paint)

        if (screen == 0) {
            drawMenu(canvas, w, h)
        } else {
            drawGame(canvas, w, h)
            tick()
            postInvalidateDelayed(16)
        }
    }

    private fun drawMenu(c: Canvas, w: Float, h: Float) {
        drawText(c, "PIXEL\nARCADE", w * 0.08f, h * 0.08f, w * 0.10f, white, true)
        drawText(c, "FOUR GAMES · ONE POCKET", w * 0.085f, h * 0.18f, w * 0.032f, muted)

        val gap = h * 0.018f
        val cardHeight = (h * 0.59f - gap * 3f) / 4f
        val top = h * 0.25f

        for (i in 0 until gameNames.size) {
            val y = top + i * (cardHeight + gap)
            round(c, w * 0.06f, y, w * 0.94f, y + cardHeight, 22f, panel)
            round(c, w * 0.06f, y, w * 0.085f, y + cardHeight, 22f, accents[i])
            drawText(c, "0${i + 1}", w * 0.12f, y + cardHeight * 0.25f, w * 0.033f, accents[i], true)
            drawText(c, gameNames[i], w * 0.21f, y + cardHeight * 0.25f, w * 0.048f, white, true)
            drawText(c, descriptions[i], w * 0.21f, y + cardHeight * 0.54f, w * 0.025f, muted)
            drawText(c, "BEST ${prefs.getInt("best_$i", 0)}", w * 0.21f, y + cardHeight * 0.82f, w * 0.024f, accents[i], true)
            drawText(c, "›", w * 0.89f, y + cardHeight * 0.35f, w * 0.065f, white, true, Paint.Align.CENTER)
        }

        drawText(c, "TAP A GAME TO PLAY", w * 0.08f, h * 0.93f, w * 0.027f, muted, true)
    }

    private fun drawGame(c: Canvas, w: Float, h: Float) {
        drawText(c, "‹  ARCADE", w * 0.06f, h * 0.045f, w * 0.037f, accents[game], true)
        drawText(c, gameNames[game], w * 0.06f, h * 0.105f, w * 0.07f, white, true)
        drawText(c, descriptions[game], w * 0.06f, h * 0.15f, w * 0.028f, muted)

        round(c, w * 0.06f, h * 0.19f, w * 0.94f, h * 0.275f, 18f, panel)

        val stats = when (game) {
            0 -> "DISTANCE ${score}m"
            1 -> "WAVE $wave • CASTLE ${castleHp}♥ • SCORE $score"
            2 -> "CLUE ${clueIndex + 1}/3 • SCORE $score"
            else -> "GOALS $goals • SHOTS $attempts/5"
        }
        drawText(c, stats, w * 0.095f, h * 0.234f, w * 0.03f, white, true)

        when (game) {
            0 -> drawRoad(c, w, h)
            1 -> drawCastle(c, w, h)
            2 -> drawPuzzle(c, w, h)
            else -> drawPenalty(c, w, h)
        }

        if (screen == 2) {
            round(c, w * 0.12f, h * 0.4f, w * 0.88f, h * 0.6f, 22f, Color.rgb(31, 37, 51))
            drawText(c, endMessage, w * 0.5f, h * 0.48f, w * 0.055f, white, true, Paint.Align.CENTER)
            drawText(c, "TAP TO PLAY AGAIN", w * 0.5f, h * 0.54f, w * 0.031f, accents[game], true, Paint.Align.CENTER)
        } else {
            when (game) {
                0 -> {
                    button(c, w * 0.08f, h * 0.86f, w * 0.46f, h * 0.94f, "◀ LEFT", panel, white)
                    button(c, w * 0.54f, h * 0.86f, w * 0.92f, h * 0.94f, "RIGHT ▶", panel, white)
                }
                1 -> {
                    button(c, w * 0.08f, h * 0.86f, w * 0.52f, h * 0.94f, "FIRE", panel, white)
                    button(c, w * 0.58f, h * 0.86f, w * 0.92f, h * 0.94f, "SHIELD", mint, bg)
                }
                2 -> {
                    button(c, w * 0.08f, h * 0.86f, w * 0.92f, h * 0.94f, if (hintVisible) "HINT: READ THE CLUE AGAIN" else "NEED A HINT?", panel, white)
                }
                else -> {
                    button(c, w * 0.06f, h * 0.86f, w * 0.34f, h * 0.94f, "LEFT", panel, white)
                    button(c, w * 0.38f, h * 0.86f, w * 0.66f, h * 0.94f, "CENTER", panel, white)
                    button(c, w * 0.70f, h * 0.86f, w * 0.94f, h * 0.94f, "RIGHT", panel, white)
                }
            }
        }
    }

    private fun drawRoad(c: Canvas, w: Float, h: Float) {
        val top = h * 0.31f
        val bottom = h * 0.82f
        round(c, w * 0.09f, top, w * 0.91f, bottom, 22f, Color.rgb(35, 41, 63))

        paint.color = Color.rgb(125, 135, 160)
        paint.strokeWidth = 3f
        paint.alpha = 140
        for (i in 1..2) {
            val x = w * (0.09f + 0.82f * i / 3f)
            var y = top + roadOffset
            while (y < bottom) {
                c.drawLine(x, y, x, y + 18f, paint)
                y += 52f
            }
        }
        paint.alpha = 255

        if (obstacleY > -0.1f) drawCar(c, laneX(obstacleLane), top + (bottom - top) * obstacleY, w * 0.105f, red)
        drawCar(c, laneX(lane), bottom - h * 0.085f, w * 0.12f, violet)
    }

    private fun drawCar(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        round(c, cx - size * 0.4f, cy - size * 0.55f, cx + size * 0.4f, cy + size * 0.55f, 12f, color)
        round(c, cx - size * 0.22f, cy - size * 0.2f, cx + size * 0.22f, cy + size * 0.05f, 7f, Color.rgb(220, 225, 255))
        paint.color = Color.BLACK
        c.drawCircle(cx - size * 0.35f, cy - size * 0.24f, size * 0.08f, paint)
        c.drawCircle(cx + size * 0.35f, cy - size * 0.24f, size * 0.08f, paint)
        c.drawCircle(cx - size * 0.35f, cy + size * 0.24f, size * 0.08f, paint)
        c.drawCircle(cx + size * 0.35f, cy + size * 0.24f, size * 0.08f, paint)
    }

    private fun drawCastle(c: Canvas, w: Float, h: Float) {
        val top = h * 0.31f
        val bottom = h * 0.82f
        round(c, w * 0.08f, top, w * 0.92f, bottom, 22f, Color.rgb(24, 42, 48))

        paint.color = Color.rgb(75, 104, 96)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        val path = android.graphics.Path().apply {
            moveTo(w * 0.15f, top + h * 0.08f)
            lineTo(w * 0.42f, top + h * 0.08f)
            lineTo(w * 0.52f, h * 0.62f)
            lineTo(w * 0.86f, h * 0.62f)
            lineTo(w * 0.86f, bottom - h * 0.06f)
        }
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        round(c, w * 0.78f, h * 0.58f, w * 0.88f, h * 0.73f, 8f, gold)

        enemies.forEach { enemy ->
            val t = enemy.progress.coerceIn(0f, 1f)
            val x = w * 0.15f + t * w * 0.70f
            val y = top + h * 0.08f + sin(t * PI).toFloat() * h * 0.24f + t * h * 0.13f
            paint.color = red
            c.drawCircle(x, y, w * 0.035f, paint)
            paint.color = white
            c.drawCircle(x - w * 0.01f, y - h * 0.008f, w * 0.008f, paint)
        }

        if (System.currentTimeMillis() < shieldUntil) {
            paint.color = mint
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            c.drawCircle(w * 0.83f, h * 0.65f, w * 0.09f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawPuzzle(c: Canvas, w: Float, h: Float) {
        round(c, w * 0.07f, h * 0.31f, w * 0.93f, h * 0.66f, 22f, panel)
        drawText(c, "CASE FILE #${100 + clueIndex}", w * 0.12f, h * 0.37f, w * 0.03f, accents[2], true)
        wrapped(c, clues[clueIndex], w * 0.12f, h * 0.43f, w * 0.75f, w * 0.042f, white)

        for (i in 0 until options[clueIndex].size) {
            val y = h * (0.68f + i * 0.07f)
            button(c, w * 0.08f, y, w * 0.92f, y + h * 0.05f, "${i + 1}  ${options[clueIndex][i]}", panel, white)
        }
    }

    private fun drawPenalty(c: Canvas, w: Float, h: Float) {
        round(c, w * 0.08f, h * 0.31f, w * 0.92f, h * 0.78f, 22f, Color.rgb(18, 64, 55))

        paint.color = Color.rgb(75, 146, 112)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        c.drawRect(w * 0.18f, h * 0.38f, w * 0.82f, h * 0.58f, paint)
        c.drawLine(w * 0.5f, h * 0.38f, w * 0.5f, h * 0.58f, paint)
        c.drawLine(w * 0.18f, h * 0.48f, w * 0.82f, h * 0.48f, paint)

        paint.style = Paint.Style.FILL
        val keeperX = w * (0.18f + (keeper + 0.5f) * 0.2f)
        paint.color = gold
        c.drawCircle(keeperX, h * 0.48f, w * 0.04f, paint)

        paint.color = white
        c.drawCircle(w * 0.5f, h * 0.69f, w * 0.04f, paint)
        drawText(c, shotText.ifEmpty { "AIM YOUR SHOT" }, w * 0.5f, h * 0.75f, w * 0.035f, white, true, Paint.Align.CENTER)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame).coerceIn(0L, 50L) / 1000f)
        lastFrame = now

        if (screen != 1) return

        when (game) {
            0 -> {
                obstacleY += speed * dt
                roadOffset = (roadOffset + dt * 90f) % 52f
                if (obstacleY > 1f) {
                    score += 1
                    obstacleY = -0.16f
                    obstacleLane = Random.nextInt(0, 3)
                    speed = (speed + 0.015f).coerceAtMost(0.88f)
                }
                if (obstacleY > 0.76f && obstacleLane == lane) {
                    finish("CRASH!")
                }
            }
            1 -> {
                spawnTimer -= dt
                if (spawnTimer <= 0f) {
                    enemies.add(Enemy(0f))
                    spawnTimer = (1.55f - wave * 0.06f).coerceAtLeast(0.55f)
                }

                val iterator = enemies.iterator()
                while (iterator.hasNext()) {
                    val enemy = iterator.next()
                    enemy.progress += dt * (0.12f + wave * 0.008f)
                    if (enemy.progress >= 1f) {
                        iterator.remove()
                        if (System.currentTimeMillis() >= shieldUntil) castleHp--
                        if (castleHp <= 0) {
                            finish("GATE BREACHED")
                            return
                        }
                    }
                }
                if (score >= wave * 5) wave += 1
            }
            2 -> Unit
            3 -> Unit
        }
    }

    private fun startGame(index: Int) {
        game = index
        screen = 1
        score = 0
        lastFrame = 0L
        endMessage = ""

        when (index) {
            0 -> {
                lane = 1
                obstacleLane = Random.nextInt(0, 3)
                obstacleY = -0.18f
                speed = 0.48f
            }
            1 -> {
                enemies.clear()
                castleHp = 5
                wave = 1
                spawnTimer = 1.2f
                shieldUntil = 0L
            }
            2 -> {
                clueIndex = 0
                hintVisible = false
            }
            3 -> {
                attempts = 0
                goals = 0
                keeper = Random.nextInt(0, 3)
                shotText = ""
            }
        }
        invalidate()
    }

    private fun finish(message: String) {
        screen = 2
        endMessage = message
        val key = "best_$game"
        val prev = prefs.getInt(key, 0)
        if (score > prev) prefs.edit().putInt(key, score).apply()
        invalidate()
    }

    fun backToMenu(): Boolean {
        if (screen != 0) {
            screen = 0
            invalidate()
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true

        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        if (screen == 0) {
            val gap = h * 0.018f
            val cardHeight = (h * 0.59f - gap * 3f) / 4f
            val top = h * 0.25f
            val index = ((y - top) / (cardHeight + gap)).toInt()
            if (index in 0..3) startGame(index)
            return true
        }

        if (screen == 2) {
            startGame(game)
            return true
        }

        when (game) {
            0 -> {
                if (x < w * 0.5f) lane = (lane - 1).coerceAtLeast(0) else lane = (lane + 1).coerceAtMost(2)
            }
            1 -> {
                if (x > w * 0.58f && y > h * 0.84f) {
                    shieldUntil = System.currentTimeMillis() + 2500L
                } else if (y > h * 0.31f && y < h * 0.82f) {
                    val target = enemies.minByOrNull { it.progress }
                    if (target != null) {
                        enemies.remove(target)
                        score += 1
                    }
                }
            }
            2 -> {
                if (y > h * 0.84f) {
                    hintVisible = true
                } else if (y > h * 0.68f && y < h * 0.84f) {
                    val picked = ((y - h * 0.68f) / (h * 0.06f)).toInt()
                    if (picked in 0..2) {
                        if (picked == correct[clueIndex]) {
                            score += 100
                            clueIndex += 1
                            hintVisible = false
                            if (clueIndex >= clues.size) finish("CASE SOLVED")
                        } else {
                            hintVisible = true
                        }
                    }
                }
            }
            3 -> {
                if (y > h * 0.82f) {
                    val picked = when {
                        x < w * 0.34f -> 0
                        x < w * 0.66f -> 1
                        else -> 2
                    }
                    attempts += 1
                    if (picked == keeper) {
                        shotText = "SAVED!"
                    } else {
                        shotText = "GOAL!"
                        goals += 1
                        score += 1
                    }
                    keeper = Random.nextInt(0, 3)
                    if (attempts >= 5) finish("FULL TIME")
                }
            }
        }

        invalidate()
        return true
    }

    private fun button(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, label: String, color: Int, textColor: Int) {
        round(c, left, top, right, bottom, 18f, color)
        drawText(c, label, (left + right) * 0.5f, (top + bottom) * 0.5f, (right - left) * 0.075f, textColor, true, Paint.Align.CENTER)
    }

    private fun round(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        c.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
    }

    private fun drawText(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        for ((i, line) in value.split("\n").withIndex()) {
            c.drawText(line, x, y + i * size * 1.08f, paint)
        }
    }

    private fun wrapped(c: Canvas, value: String, x: Float, y: Float, maxWidth: Float, size: Float, color: Int) {
        paint.color = color
        paint.textSize = size
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        var line = ""
        var row = 0
        for (word in value.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                c.drawText(line, x, y + row * size * 1.35f, paint)
                line = word
                row += 1
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) c.drawText(line, x, y + row * size * 1.35f, paint)
    }

    private fun laneX(laneIndex: Int): Float = 0.09f + 0.82f * ((laneIndex + 0.5f) / 3f)
}
