package com.sorimpower.app.feature.bodylog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

private val Context.bodyLogDataStore by preferencesDataStore("body_log_settings")

data class BodyLogData(
    val weights: List<WeightEntryEntity>,
    val meals: List<MealWithDetails>,
    val goal: WeightGoalEntity?,
    val mounjaroInjections: List<MounjaroInjectionEntity>,
    val weightsHidden: Boolean,
)

data class MealItemInput(val name: String, val amount: String = "")

class BodyLogRepository(private val context: Context) {
    private val dao = BodyLogDatabase.get(context).dao()
    private val weightsHiddenKey = booleanPreferencesKey("weights_hidden")
    private val weightsHidden = context.bodyLogDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[weightsHiddenKey] ?: false }
    val data = combine(
        dao.observeWeights(),
        dao.observeMeals(),
        dao.observeActiveGoal(),
        dao.observeMounjaroInjections(),
        weightsHidden,
        ::BodyLogData,
    )

    init {
        CoroutineScope(Dispatchers.IO).launch { cleanupOrphanedMealPhotos() }
    }

    suspend fun setWeightsHidden(hidden: Boolean) {
        context.bodyLogDataStore.edit { preferences -> preferences[weightsHiddenKey] = hidden }
    }

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

    suspend fun saveMounjaroInjection(
        existing: MounjaroInjectionEntity? = null,
        injectedAt: Long,
        doseMg: Double,
        sideEffects: Set<String>,
        note: String?,
        reminderEnabled: Boolean,
        reminderIntervalWeeks: Int,
    ) {
        val now = System.currentTimeMillis()
        dao.upsertMounjaroInjection(MounjaroInjectionEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            injectedAt = injectedAt,
            doseMg = doseMg,
            sideEffects = sideEffects.joinToString("|"),
            note = note?.trim()?.take(300)?.ifBlank { null },
            reminderEnabled = reminderEnabled,
            reminderIntervalWeeks = reminderIntervalWeeks.coerceIn(1, 4),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        ))
    }

    suspend fun updateMounjaroReminder(injection: MounjaroInjectionEntity, enabled: Boolean, intervalWeeks: Int) {
        dao.upsertMounjaroInjection(injection.copy(
            reminderEnabled = enabled,
            reminderIntervalWeeks = intervalWeeks.coerceIn(1, 4),
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun deleteMounjaroInjection(injection: MounjaroInjectionEntity) {
        dao.deleteMounjaroInjection(injection)
    }

    suspend fun latestMounjaroInjection(): MounjaroInjectionEntity? = dao.latestMounjaroInjection()

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
        val removedPhotos = existing?.photos.orEmpty().filterNot { it.id in retainedPhotoIds }
        val available = (3 - retainedPhotos.size).coerceAtLeast(0)
        val imported = photoUris.take(available).mapIndexedNotNull { index, uri -> importPhoto(uri, mealId, retainedPhotos.size + index) }
        val photos = retainedPhotos.mapIndexed { index, photo -> photo.copy(sortOrder = index) } + imported
        try {
            dao.replaceMeal(meal, entities, photos)
            removedPhotos.forEach(::deletePhotoFiles)
        } finally {
            cleanupOrphanedMealPhotos()
        }
    }

    suspend fun deleteMeal(meal: MealWithDetails) = withContext(Dispatchers.IO) {
        dao.deleteMealById(meal.meal.id)
        meal.photos.forEach { photo ->
            deletePhotoFiles(photo)
        }
        cleanupOrphanedMealPhotos()
    }

    fun createCameraUri(): Pair<Uri, File> {
        val directory = File(context.cacheDir, "meal_camera").apply { mkdirs() }
        val file = File.createTempFile("meal_", ".jpg", directory)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) to file
    }

    private fun importPhoto(uri: Uri, mealId: String, index: Int): MealPhotoEntity? = runCatching {
        val bitmap = decodeMealPhoto(uri) ?: return null
        val scaled = bitmap.scaledTo(MAX_PHOTO_SIDE)
        val thumb = scaled.scaledTo(THUMBNAIL_SIDE)
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

    private fun decodeMealPhoto(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (targetWidth, targetHeight) = targetPhotoSize(info.size.width, info.size.height)
                decoder.setTargetSize(targetWidth, targetHeight)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = photoSampleSize(bounds.outWidth, bounds.outHeight)
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    private fun targetPhotoSize(width: Int, height: Int): Pair<Int, Int> {
        val halfWidth = (width / 2).coerceAtLeast(1)
        val halfHeight = (height / 2).coerceAtLeast(1)
        val largest = maxOf(halfWidth, halfHeight)
        if (largest <= MAX_PHOTO_SIDE) return halfWidth to halfHeight
        val ratio = MAX_PHOTO_SIDE.toFloat() / largest
        return (halfWidth * ratio).toInt().coerceAtLeast(1) to (halfHeight * ratio).toInt().coerceAtLeast(1)
    }

    private fun photoSampleSize(width: Int, height: Int): Int {
        var sampleSize = 2
        while (maxOf(width / sampleSize, height / sampleSize) > MAX_PHOTO_SIDE) sampleSize *= 2
        return sampleSize
    }

    private fun deletePhotoFiles(photo: MealPhotoEntity) {
        File(photo.localPath).delete()
        File(photo.thumbnailPath).delete()
    }

    private suspend fun cleanupOrphanedMealPhotos() {
        val referencedPaths = dao.allMealPhotoPaths()
            .flatMap { listOf(it.localPath, it.thumbnailPath) }
            .toSet()
        File(context.filesDir, "meal_photos").listFiles()
            ?.filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) && it.absolutePath !in referencedPaths }
            ?.forEach(File::delete)
    }

    private fun Bitmap.scaledTo(maxSide: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxSide) return this
        val ratio = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    private companion object {
        const val MAX_PHOTO_SIDE = 2048
        const val THUMBNAIL_SIDE = 360
    }
}
