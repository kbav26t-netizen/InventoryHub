package com.inventory.hub.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "products", indices = [Index(value = ["barcode"], unique = true)])
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val article: String,
    val quantity: Int
)

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    fun getByBarcodeSync(barcode: String): ProductEntity?
    
    @Query("SELECT COUNT(*) FROM products")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM products")
    suspend fun clearAll()
}

@Database(entities = [ProductEntity::class], version = 1, exportSchema = false)
abstract class ProductDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile private var INSTANCE: ProductDatabase? = null
        fun getInstance(context: Context): ProductDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, ProductDatabase::class.java, "hub_products")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
