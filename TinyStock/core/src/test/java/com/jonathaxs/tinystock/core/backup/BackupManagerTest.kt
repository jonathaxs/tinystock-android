package com.jonathaxs.tinystock.core.backup

import com.jonathaxs.tinystock.core.data.InMemoryImageFileStore
import com.jonathaxs.tinystock.core.data.InMemoryTinyStockStore
import com.jonathaxs.tinystock.core.data.TinyStockStore
import com.jonathaxs.tinystock.core.model.OrderFulfillment
import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesChannel
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.SalesOrderItem
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StockMovementKind
import com.jonathaxs.tinystock.core.model.StoreLifecycleState
import com.jonathaxs.tinystock.core.model.StoreProfile
import com.jonathaxs.tinystock.core.support.assertDecimal
import com.jonathaxs.tinystock.core.support.expectError
import com.jonathaxs.tinystock.core.support.withStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Porte dos testes v2 de `BackupManagerTests.swift`, mais a leitura de um arquivo gravado pelo encoder do
 * iOS (`resources/backup/ios_v2.json`, gerado pelo codigo Swift do app).
 */
class BackupManagerTest {

    private val reference: Instant = Instant.ofEpochSecond(1_789_000_000L, 125_000_000)

    private val activeStoreID = UUID.fromString("A1000000-0000-4000-8000-00000000000A")
    private val trashedStoreID = UUID.fromString("B2000000-0000-4000-8000-00000000000B")
    private val productID = UUID.fromString("C3000000-0000-4000-8000-00000000000C")

    private fun iosFixture(): ByteArray =
        checkNotNull(javaClass.getResource("/backup/ios_v2.json")).readBytes()

    private fun iosPayload(): BackupPayload = BackupManager.decode(iosFixture())

    @Test
    fun arquivoDoIosEhLidoSemPerderPrecisao() {
        val payload = iosPayload()

        assertEquals(2, payload.version)
        assertEquals(reference, payload.exportedAt)
        assertEquals(activeStoreID, payload.selectedStoreID)
        assertEquals(BackupSummary(storeCount = 2, productCount = 2, variantCount = 2, orderCount = 1), payload.summary)

        val active = payload.stores.first { it.id == activeStoreID }
        assertEquals("Loja ativa ção", active.name)
        assertEquals(reference.plusMillis(500), active.updatedAt)
        assertEquals(2, active.sortOrder)
        // Base64 com a barra escapada ("\/"), como o JSONEncoder do iOS grava.
        assertArrayEquals(byteArrayOf(0x02, 0xFB.toByte(), 0xFF.toByte()), active.imageData)

        val product = payload.products.first { it.id == productID }
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x01), product.imageData)
        assertDecimal("10.20", product.costPrice)
        assertDecimal("39.90", product.salePrice)

        val order = payload.orders.single()
        assertDecimal("7.98", order.channelFeeAmount)
        assertDecimal("10", order.channelFeePercentage)
        assertEquals("futureStatus", order.status)
        assertNull(order.cancelledAt)
        assertEquals(reference, order.productionStartedAt)
    }

    @Test
    fun exportacaoPreservaDominioCompletoEhEstavel() = withStore { store ->
        val images = InMemoryImageFileStore()
        val activeStore = StoreProfile(
            name = "Loja Principal", imageFileName = images.write(byteArrayOf(0x01, 0x02)), sortOrder = 4,
            createdAt = reference, updatedAt = reference.plusSeconds(1),
        )
        val archivedStore = StoreProfile(
            name = "Loja arquivada", imageFileName = images.write(byteArrayOf(0x03)), isArchived = true,
            archivedAt = reference.plusSeconds(2), sortOrder = 9,
            createdAt = reference.plusSeconds(2), updatedAt = reference.plusSeconds(3),
        )
        val product = Product(
            storeID = activeStore.id, name = "Produto Premium", category = "Legado", quantity = 4,
            minimumStock = 1, costPrice = BigDecimal("80.25"), salePrice = BigDecimal("179.90"),
            imageFileName = images.write(byteArrayOf(0x04, 0x05)),
            createdAt = reference.plusSeconds(4), updatedAt = reference.plusSeconds(5),
        )
        val variant = ProductVariant(
            storeID = activeStore.id, productID = product.id, name = ProductVariant.INTERNAL_DEFAULT_NAME,
            isDefault = true, quantity = 3, createdAt = reference.plusSeconds(6), updatedAt = reference.plusSeconds(7),
        )
        val movement = StockMovement(
            storeID = activeStore.id, productID = product.id, variantID = variant.id,
            kindRawValue = "futureMovement", quantityDelta = 3, balanceAfter = 3, note = "Lote inicial",
            referenceID = UUID.randomUUID(), createdAt = reference.plusSeconds(8),
        )
        val order = SalesOrder(
            storeID = activeStore.id, channelRawValue = "futureChannel", customChannelName = "Feira",
            fulfillmentRawValue = "futureFulfillment", statusRawValue = "futureStatus", buyerName = "Ana",
            externalReference = "PED-42", orderedAt = reference.plusSeconds(9),
            productionDueAt = reference.plusSeconds(10), shippingDueAt = reference.plusSeconds(11),
            productionStartedAt = reference.plusSeconds(14), producedAt = reference.plusSeconds(15),
            shippedAt = reference.plusSeconds(16), completedAt = reference.plusSeconds(17),
            cancelledAt = reference.plusSeconds(18), trackingCode = "BR123", note = "Embalar bem",
            channelFeePercentage = BigDecimal("12.5"), channelFeeAmount = BigDecimal("22.49"),
            createdAt = reference.plusSeconds(12), updatedAt = reference.plusSeconds(13),
            items = listOf(
                SalesOrderItem(
                    storeID = activeStore.id, productID = product.id, variantID = variant.id,
                    productName = "Produto Premium", variantName = "Preta", unitPrice = BigDecimal("179.90"),
                    unitCost = BigDecimal("80.25"), quantity = 2, position = 3,
                ),
            ),
        )
        store.saveStore(activeStore)
        store.saveStore(archivedStore)
        store.saveProduct(product)
        store.saveVariant(variant)
        store.saveMovement(movement)
        store.saveOrder(order)

        val first = BackupManager.export(activeStore.id, images, store, exportedAt = reference)
        val second = BackupManager.export(activeStore.id, images, store, exportedAt = reference)
        val payload = BackupManager.decode(first)

        assertArrayEquals(first, second)
        assertEquals(2, payload.version)
        assertEquals(reference, payload.exportedAt)
        assertEquals(activeStore.id, payload.selectedStoreID)
        assertEquals(BackupSummary(storeCount = 2, productCount = 1, variantCount = 1, orderCount = 1), payload.summary)
        val activeSnapshot = payload.stores.first { it.id == activeStore.id }
        assertArrayEquals(byteArrayOf(0x01, 0x02), activeSnapshot.imageData)
        assertEquals(4, activeSnapshot.sortOrder)
        val archivedSnapshot = payload.stores.first { it.id == archivedStore.id }
        assertTrue(archivedSnapshot.isArchived)
        assertEquals(archivedStore.archivedAt, archivedSnapshot.archivedAt)
        assertEquals(listOf("Produto Premium"), payload.products.map { it.name })
        assertArrayEquals(byteArrayOf(0x04, 0x05), payload.products.first().imageData)
        assertDecimal("80.25", payload.products.first().costPrice)
        assertEquals(3, payload.variants.first().quantity)
        assertTrue(payload.variants.first().isDefault)
        assertEquals("futureMovement", payload.stockMovements.first().kind)
        assertEquals(movement.referenceID, payload.stockMovements.first().referenceID)

        val orderSnapshot = payload.orders.single()
        val itemSnapshot = orderSnapshot.items.single()
        assertEquals("futureChannel", orderSnapshot.channel)
        assertEquals("futureFulfillment", orderSnapshot.fulfillment)
        assertEquals("futureStatus", orderSnapshot.status)
        assertEquals(reference.plusSeconds(14), orderSnapshot.productionStartedAt)
        assertEquals(reference.plusSeconds(18), orderSnapshot.cancelledAt)
        assertDecimal("22.49", orderSnapshot.channelFeeAmount)
        assertEquals(variant.id, itemSnapshot.variantID)
        assertEquals(3, itemSnapshot.position)

        // Mesmo formato do iOS: UUID em maiusculas, data em segundos e dinheiro como numero.
        val text = first.decodeToString()
        assertTrue(text.contains("\"id\": \"${activeStore.id.toString().uppercase()}\""))
        assertTrue(text.contains("\"exportedAt\": 1789000000.125"))
        assertTrue(text.contains("\"salePrice\": 179.9"))
        // Opcional vazio fica fora do arquivo, como no iOS.
        assertFalse(text.contains("null"))
    }

    @Test
    fun exportacaoSemLojaAtivaEhRecusada() = withStore { store ->
        store.saveStore(StoreProfile(name = "Arquivada", isArchived = true, archivedAt = reference))

        expectError(BackupError.InvalidFile) {
            BackupManager.export(UUID.randomUUID(), InMemoryImageFileStore(), store, exportedAt = reference)
        }
    }

    @Test
    fun restauracaoSubstituiBancoERemontaPedidos() = withStore { store ->
        val images = InMemoryImageFileStore()
        val oldStore = StoreProfile(name = "Loja antiga", imageFileName = images.write(byteArrayOf(0x09)))
        store.saveStore(oldStore)
        store.saveProduct(Product(storeID = oldStore.id, name = "Produto antigo"))
        val payload = iosPayload()

        val result = BackupManager.apply(payload, images, store)

        assertEquals(payload.selectedStoreID, result.selectedStoreID)
        assertEquals(payload.summary, result.summary)
        assertEquals(2, store.stores().size)
        assertNull(store.store(oldStore.id))
        assertEquals(2, store.allProducts().size)
        assertEquals(2, store.allVariants().size)
        assertEquals(2, store.allMovements().size)
        val restoredOrder = store.allOrders().single()
        val restoredItem = restoredOrder.itemList.single()
        assertEquals("futureChannel", restoredOrder.channelRawValue)
        assertEquals("futureFulfillment", restoredOrder.fulfillmentRawValue)
        assertEquals("futureStatus", restoredOrder.statusRawValue)
        assertEquals("Caneca", restoredItem.productName)
        assertDecimal("79.80", restoredOrder.total)
        assertEquals(2, store.store(activeStoreID)?.sortOrder)
        val restoredTrash = checkNotNull(store.store(trashedStoreID))
        assertEquals(StoreLifecycleState.Trashed, restoredTrash.lifecycleState)
        assertTrue(restoredTrash.wasArchivedBeforeTrash)

        // Fotos viram arquivos; a da loja apagada sai junto.
        val activeImage = checkNotNull(store.store(activeStoreID)?.imageFileName)
        assertArrayEquals(byteArrayOf(0x02, 0xFB.toByte(), 0xFF.toByte()), images.read(activeImage))
        assertNull(restoredTrash.imageFileName)
        assertNull(oldStore.imageFileName?.let { images.read(it) })
        assertEquals(3, images.files.size)
    }

    @Test
    fun fotoDaVariacaoAcompanhaExportacaoERestauracao() = withStore { store ->
        val images = InMemoryImageFileStore()
        val shop = StoreProfile(name = "Loja")
        val product = Product(storeID = shop.id, name = "Bolsa")
        store.saveStore(shop)
        store.saveProduct(product)
        store.saveVariant(
            ProductVariant(
                storeID = shop.id, productID = product.id, name = "Azul", quantity = 2,
                imageFileName = images.write(byteArrayOf(0x0A, 0x0B)),
            ),
        )

        val payload = BackupManager.decode(BackupManager.export(shop.id, images, store, exportedAt = reference))
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), payload.variants.single().imageData)

        val restored = InMemoryTinyStockStore()
        val restoredImages = InMemoryImageFileStore()
        BackupManager.apply(payload, restoredImages, restored)
        val variant = restored.allVariants().single()
        assertArrayEquals(byteArrayOf(0x0A, 0x0B), restoredImages.read(checkNotNull(variant.imageFileName)))
    }

    @Test
    fun arquivoV2AntigoSemCamposOpcionaisContinuaCompativel() {
        val storeID = UUID.randomUUID().toString()
        val productID = UUID.randomUUID().toString()
        // Sem ordem de loja, sem marca de lixeira, sem marcador de variacao e sem foto. UUID minusculo e
        // datas em texto ISO, com fuso e com fracao.
        val json = """
            {"version":2,"exportedAt":"2026-09-21T00:30:00-03:00","sales":[],"selectedStoreID":"$storeID",
             "stores":[{"id":"$storeID","name":"Loja","isArchived":false,
                        "createdAt":"2026-09-21T03:30:00Z","updatedAt":"2026-09-21T03:30:00.250Z"}],
             "products":[{"id":"$productID","storeID":"$storeID","name":"Caneca","category":"","quantity":0,
                          "minimumStock":0,"costPrice":10.2,"salePrice":39.9,"createdAt":0,"updatedAt":0}],
             "variants":[{"id":"${UUID.randomUUID()}","storeID":"$storeID","productID":"$productID",
                          "name":"Azul","quantity":1,"createdAt":0,"updatedAt":0}],
             "futureField":{"anything":true}}
        """.trimIndent()

        val payload = BackupManager.decode(json.encodeToByteArray())

        assertEquals(Instant.parse("2026-09-21T03:30:00Z"), payload.exportedAt)
        val shop = payload.stores.single()
        assertEquals(0, shop.sortOrder)
        assertNull(shop.trashedAt)
        assertFalse(shop.wasArchivedBeforeTrash)
        assertEquals(Instant.parse("2026-09-21T03:30:00.250Z"), shop.updatedAt)
        val variant = payload.variants.single()
        assertFalse(variant.isDefault)
        assertNull(variant.imageData)
        assertTrue(payload.orders.isEmpty())
        assertTrue(payload.stockMovements.isEmpty())
    }

    @Test
    fun restauracaoMantemIdentidadeEOrdemDoCatalogo() = withStore { store ->
        store.saveStore(StoreProfile(id = activeStoreID, name = "Nome anterior"))
        store.saveProduct(Product(id = productID, storeID = activeStoreID, name = "Produto anterior", sortOrder = 5))

        BackupManager.apply(iosPayload(), InMemoryImageFileStore(), store)

        assertEquals("Loja ativa ção", store.store(activeStoreID)?.name)
        val product = checkNotNull(store.product(productID))
        assertEquals("Caneca", product.name)
        // O iOS nao leva a ordem do catalogo no backup: o produto que ja existia mantem a dele.
        assertEquals(5, product.sortOrder)
        assertEquals(0, store.allProducts().first { it.id != productID }.sortOrder)
    }

    @Test
    fun falhaAoGravarDesfazTodaRestauracao() = withStore { inner ->
        val images = InMemoryImageFileStore()
        val oldImage = images.write(byteArrayOf(0x07))
        val oldStore = StoreProfile(name = "Loja preservada", imageFileName = oldImage)
        inner.saveStore(oldStore)
        inner.saveProduct(Product(storeID = oldStore.id, name = "Produto preservado"))
        val failing = object : TinyStockStore by inner {
            override suspend fun saveOrder(order: SalesOrder) = throw IllegalStateException("falha simulada")
        }

        try {
            BackupManager.apply(iosPayload(), images, failing)
            throw AssertionError("Esperava a falha simulada")
        } catch (error: IllegalStateException) {
            assertEquals("falha simulada", error.message)
        }

        assertEquals(listOf("Loja preservada"), inner.stores().map { it.name })
        assertEquals(listOf("Produto preservado"), inner.allProducts().map { it.name })
        assertTrue(inner.allOrders().isEmpty())
        // As fotos novas saem; a antiga continua.
        assertEquals(setOf(oldImage), images.files.keys)
    }

    @Test
    fun v2RecusaIdentificadoresDuplicadosEAssociacoesOrfas() = withStore { store ->
        val valid = iosPayload()
        val duplicateProduct = valid.copy(products = valid.products + valid.products[0])
        val original = valid.variants[0]
        val orphanVariant = BackupPayload.ProductVariantSnapshot(
            createdAt = original.createdAt, id = original.id, name = original.name,
            productID = UUID.randomUUID(), quantity = original.quantity, storeID = original.storeID,
            updatedAt = original.updatedAt,
        )
        val orphanPayload = valid.copy(variants = listOf(orphanVariant) + valid.variants.drop(1))

        expectError(BackupError.InvalidFile) { BackupManager.apply(duplicateProduct, InMemoryImageFileStore(), store) }
        expectError(BackupError.InvalidFile) { BackupManager.apply(orphanPayload, InMemoryImageFileStore(), store) }
        assertFalse(isValidV2(duplicateProduct))
        assertFalse(isValidV2(orphanPayload))
        assertTrue(store.stores().isEmpty())
    }

    @Test
    fun v2RecusaLojaSelecionadaArquivada() = withStore { store ->
        val payload = iosPayload().copy(selectedStoreID = trashedStoreID)

        expectError(BackupError.InvalidFile) { BackupManager.apply(payload, InMemoryImageFileStore(), store) }
    }

    @Test
    fun versaoDesconhecidaV1EJsonInvalidoSaoRecusados() {
        val unknown = iosFixture().decodeToString().replace("\"version\" : 2", "\"version\" : 3")
        expectError(BackupError.UnsupportedVersion(3)) { BackupManager.decode(unknown.encodeToByteArray()) }

        val legacy = """{"version":1,"exportedAt":0,"products":[],"sales":[]}"""
        expectError(BackupError.UnsupportedVersion(1)) { BackupManager.decode(legacy.encodeToByteArray()) }

        val invalid = listOf(
            "nao e json",
            "[]",
            """{"version":"2"}""",
            """{"version":2}""",
            // Dinheiro entre aspas, UUID curto e foto que nao e Base64, todos recusados pelo iOS.
            iosFixture().decodeToString().replace("\"costPrice\" : 10.2", "\"costPrice\" : \"10.2\""),
            iosFixture().decodeToString().replace("A1000000-0000-4000-8000-00000000000A", "1-1-1-1-1"),
            iosFixture().decodeToString().replace("\"Cgs=\"", "\"nao base64\""),
        )
        for (text in invalid) {
            expectError(BackupError.InvalidFile) { BackupManager.decode(text.encodeToByteArray()) }
        }
        expectError(BackupError.InvalidFile) { BackupManager.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
    }

    @Test
    fun nomeSugeridoUsaADataEmUtc() {
        assertEquals(
            "tinystock-backup-2026-08-17.json",
            BackupManager.suggestedFilename(Instant.parse("2026-08-16T23:30:00-03:00")),
        )
    }

    @Test
    fun pedidoRestauradoFuncionaComOsServicos() = withStore { store ->
        // Valores conhecidos atravessam o backup como enums de verdade.
        val shop = StoreProfile(name = "Loja")
        val product = Product(storeID = shop.id, name = "Caneca", salePrice = BigDecimal("20"))
        val variant = ProductVariant(storeID = shop.id, productID = product.id, name = "Azul", quantity = 1)
        store.saveStore(shop)
        store.saveProduct(product)
        store.saveVariant(variant)
        store.saveMovement(
            StockMovement(
                storeID = shop.id, productID = product.id, variantID = variant.id,
                kindRawValue = StockMovementKind.InitialStock.rawValue, quantityDelta = 1, balanceAfter = 1,
            ),
        )
        store.saveOrder(
            SalesOrder(
                storeID = shop.id, channelRawValue = SalesChannel.Shopee.rawValue,
                fulfillmentRawValue = OrderFulfillment.Production.rawValue,
                items = listOf(SalesOrderItem.snapshot(product, variant, quantity = 1)),
            ),
        )
        val images = InMemoryImageFileStore()

        val restored = InMemoryTinyStockStore()
        BackupManager.apply(BackupManager.decode(BackupManager.export(shop.id, images, store)), images, restored)

        val order = restored.allOrders().single()
        assertEquals(SalesChannel.Shopee, order.channel)
        assertEquals(OrderFulfillment.Production, order.fulfillment)
        assertEquals(OrderFulfillment.Production.initialStatus, order.status)
        assertEquals(StockMovementKind.InitialStock, restored.allMovements().single().kind)
    }
}
