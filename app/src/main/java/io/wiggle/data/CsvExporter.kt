package io.wiggle.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.wiggle.domain.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a person's data out as CSV files the share sheet can hand to anything.
 *
 * The files go to the cache directory: they exist to be shared, not kept, and Android is free to
 * reclaim them once the receiving app is done. Each export clears the previous one first so the
 * folder cannot grow without bound.
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WiggleRepository,
) {

    /**
     * The weight file is always written, even when it is only a header row: a share that quietly
     * produces nothing looks broken. Body and water are left out when there is nothing in them.
     */
    suspend fun export(profileId: Long): List<Uri> = withContext(Dispatchers.IO) {
        val snapshot = repository.exportSnapshot(profileId)
        val slug = snapshot.profile?.name.orEmpty()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "wiggle" }
        val stamp = LocalDate.now()

        val directory = File(context.cacheDir, "export").apply {
            deleteRecursively()
            mkdirs()
        }

        buildList {
            add(directory.write("$slug-weight-$stamp.csv", Csv.weights(snapshot.weights)))
            if (snapshot.body.isNotEmpty()) {
                add(
                    directory.write(
                        "$slug-body-$stamp.csv",
                        Csv.body(snapshot.body, snapshot.customTypes, snapshot.customValues),
                    )
                )
            }
            if (snapshot.water.isNotEmpty()) {
                add(directory.write("$slug-water-$stamp.csv", Csv.water(snapshot.water)))
            }
        }
    }

    private fun File.write(name: String, content: String): Uri {
        val file = File(this, name)
        file.writeText(content)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
