package com.jonathaxs.tinystock.core.data

import com.jonathaxs.tinystock.core.model.Product
import com.jonathaxs.tinystock.core.model.ProductVariant
import com.jonathaxs.tinystock.core.model.SalesOrder
import com.jonathaxs.tinystock.core.model.StockMovement
import com.jonathaxs.tinystock.core.model.StoreProfile
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Armazenamento usado pelos servicos, no lugar do `ModelContext` do iOS. A versao real usa Room (A2);
 * os testes e as previas usam [InMemoryTinyStockStore].
 *
 * Regras:
 * - `save` insere ou substitui pelo id. `delete` de id inexistente nao faz nada.
 * - As listas voltam sem ordem garantida: quem precisa de ordem ordena, como os servicos do iOS.
 * - Pedido e gravado e lido inteiro, com as linhas ([SalesOrder.items]).
 * - [inTransaction] grava tudo ou nada: se o bloco lancar excecao, nenhuma mudanca dele fica. Pode
 *   ser aninhado; so o mais externo confirma.
 * - Os `observe` emitem a lista atual ao serem coletados e de novo a cada mudanca confirmada (equivalem
 *   ao `@Query` do SwiftUI). Sao o que as telas usam; os servicos usam as funcoes suspend.
 */
interface TinyStockStore {

    suspend fun <R> inTransaction(block: suspend () -> R): R

    // Lojas
    suspend fun stores(): List<StoreProfile>
    suspend fun store(id: UUID): StoreProfile?
    suspend fun saveStore(store: StoreProfile)
    suspend fun deleteStore(id: UUID)

    // Produtos
    suspend fun products(storeID: UUID): List<Product>
    suspend fun product(id: UUID): Product?
    suspend fun saveProduct(product: Product)
    suspend fun deleteProduct(id: UUID)

    // Variacoes
    suspend fun variants(storeID: UUID): List<ProductVariant>
    suspend fun variant(id: UUID): ProductVariant?
    suspend fun saveVariant(variant: ProductVariant)
    suspend fun deleteVariant(id: UUID)

    // Movimentacoes de estoque
    suspend fun movements(storeID: UUID): List<StockMovement>
    suspend fun movement(id: UUID): StockMovement?
    suspend fun saveMovement(movement: StockMovement)
    suspend fun deleteMovement(id: UUID)

    // Pedidos, com as linhas
    suspend fun orders(storeID: UUID): List<SalesOrder>
    suspend fun order(id: UUID): SalesOrder?
    suspend fun saveOrder(order: SalesOrder)
    suspend fun deleteOrder(id: UUID)

    // Observacao para as telas
    fun observeStores(): Flow<List<StoreProfile>>
    fun observeProducts(storeID: UUID): Flow<List<Product>>
    fun observeVariants(storeID: UUID): Flow<List<ProductVariant>>
    fun observeMovements(storeID: UUID): Flow<List<StockMovement>>
    fun observeOrders(storeID: UUID): Flow<List<SalesOrder>>
}
