package com.inventory.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.inventory.hub.data.ProductDatabase

class ProductProvider : ContentProvider() {
    private val db by lazy { context?.let { ProductDatabase.getInstance(it) } }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (uri.path != "/products" || selectionArgs.isNullOrEmpty()) return null
        
        val barcode = selectionArgs[0]
        val product = db?.productDao()?.getByBarcode(barcode) ?: return null

        val cursor = MatrixCursor(arrayOf("barcode", "name", "article", "quantity"))
        cursor.addRow(arrayOf(product.barcode, product.name, product.article, product.quantity))
        return cursor
    }

    override fun getType(uri: Uri): String? = "vnd.android.cursor.item/vnd.com.inventory.hub.product"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
