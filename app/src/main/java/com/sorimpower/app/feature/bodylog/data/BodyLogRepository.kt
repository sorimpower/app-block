package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class BodyLogData(
    val weights: List<WeightEntryEntity>,
    val meals: List<MealWithDetails>,
    val goal: WeightGoalEntity?,
)

data class MealItemInput(val name: String, val amount: String = "")

class BodyLogRepository(private val context: Context) {
    private val dao = BodyLogDatabase.get(context).dao()
    val data = combine(dao.observeWeights(), dao.observeMeals(), dao.observeActiveGoal(), ::BodyLogData)

    suspend fun saveWeight(
        id: String? = null,
        weightKg: Double,
        measuredAt: Long,
        bodyFatPercent: Double?,
        condition: String?,
        note: String?,
    ) {
        val now = System.currentTimeMillis()
        dao.upsertWeight(WeightEntryEntity(
            id = id ?: UUID.randomUUID().toString(),
            weightKg = weightKg,
            measuredAt = measuredAt,
            zoneOffsetMinutes = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(measuredAt), ZoneId.systemDefault()).offset.totalSeconds / 60,
            bodyFatPercent = bodyFatPercent,
            condition = condition,
            note = note?.trim()?.take(200)?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        ))
    }

    suspend fun deleteWeight(entry: WeightEntryEntity) = dao.deleteWeight(entry)

    suspend fun saveGoal(startWeightKg: Double, targetWeightKg: Double, targetDate: LocalDate?) {
        dao.cancelActiveGoals()
        dao.upsertGoal(WeightGoalEntity(
            id = UUID.randomUUID().toString(),
            startWeightKg = startWeightKg,
            targetWeightKg = targetWeightKg,
            startedOnEpochDay = LocalDate.now().toEpochDay(),
            targetDateEpochDay = targetDate?.toEpochDay(),
            status = "ACTIVE",
        ))
    }

    suspend fun saveMeal(
        existing: MealWithDetails? = null,
        mealType: String,
        eatenAt: Long,
        items: List<MealItemInput>,
        note: String?,
        tags: Set<String>,
        photoUris: List<Uri>,
        retainedPhotoIds: Set<String> = emptySet(),
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mealId = existing?.meal?.id ?: UUID.randomUUID().toString()
        val meal = MealEntryEntity(
            id = mealId,
            mealType = mealType,
            eatenAt = eatenAt,
            zoneOffsetMinutes = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(eatenAt), ZoneId.systemDefault()).offset.totalSeconds / 60,
            note = note?.trim()?.take(200)?.ifBlank { null },
            tags = tags.joinToString("|"),
            createdAt = existing?.meal?.createdAt ?: now,
            updatedAt = now,
        )
        val entities = items.filter { it.name.isNotBlank() }.mapIndexed { index, item ->
            MealItemEntity(UUID.randomUUID().toString(), mealId, item.name.trim().take(60), item.amount.trim().take(30).ifBlank { null }, index)
        }
        val retainedPhotos = existing?.photos.orEmpty().filter { it.id in retainedPhotoIds }
        existing?.photos.orEmpty().filterNot { it.id in retainedPhotoIds }.forEach { photo ->
            File(photo.localPath).delete()
            File(photo.thumbnailPath).delete()
        }
        val available = (3 - retainedPhotos.size).coerceAtLeast(0)
        val imported = photoUris.take(available).mapIndexedNotNull { index, uri -> importPhoto(uri, mealId, retainedPhotos.size + index) }
        val photos = retainedPhotos.mapIndexed { index, photo -> photo.copy(sortOrder = index) } + imported
        dao.replaceMeal(meal, entities, photos)
    }

    suspend fun deleteMeal(meal: MealWithDetails) = withContext(Dispatchers.IO) {
        meal.photos.forEach { photo ->
            File(photo.localPath).delete()
            File(photo.thumbnailPath).delete()
        }
        dao.deleteMealById(meal.meal.id)
    }

    fun createCameraUri(): Pair<Uri, File> {
        val directory = File(context.cacheDir, "meal_camera").apply { mkdirs() }
        val file = File.createTempFile("meal_", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to file
    }

    private fun importPhoto(uri: Uri, mealId: String, index: Int): MealPhotoEntity? = runCatching {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use(BitmapFactory::decodeStream)
        } ?: return null
        val scaled = bitmap.scaledTo(2048)
        val thumb = scaled.scaledTo(360)
        val directory = File(context.filesDir, "meal_photos").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val imageFile = File(directory, "$id.jpg")
        val thumbFile = File(directory, "${id}_thumb.jpg")
        imageFile.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        thumbFile.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 78, it) }
        val outputWidth = scaled.width
        val outputHeight = scaled.height
        if (scaled !== bitmap) scaled.recycle()
        if (thumb !== scaled) thumb.recycle()
        bitmap.recycle()
        MealPhotoEntity(id, mealId, imageFile.absolutePath, thumbFile.absolutePath, outputWidth, outputHeight, index, System.currentTimeMillis())
    }.getOrNull()

    private fun Bitmap.scaledTo(maxSide: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxSide) return this
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }
}
