package com.cehpoint.netwin.domain.repository

import com.cehpoint.netwin.data.model.PaginationParams
import com.cehpoint.netwin.data.model.PaginationResult
import com.cehpoint.netwin.data.model.PendingDeposit
import com.cehpoint.netwin.data.model.Transaction
import com.cehpoint.netwin.data.model.WithdrawalRequest
import kotlinx.coroutines.flow.Flow

// --- ADDED THIS DATA CLASS ---
// Represents the combined wallet data for real-time updates.
// You can move this to a separate file if preferred.
data class WalletData(
    val balance: Double = 0.0,
    val withdrawable: Double = 0.0,
    val bonus: Double = 0.0
)
// -----------------------------

interface WalletRepository {

    // --- ADDED NEW REAL-TIME LISTENER FUNCTION ---
    /**
     * Listens for real-time changes to the user's wallet document (balance, withdrawable, bonus).
     * Emits a [WalletData] object whenever the data changes in Firestore.
     */
    fun listenToWalletChanges(userId: String): Flow<WalletData>
    // -------------------------------------------

    // --- Existing Functions (Unchanged) ---
    fun getWalletBalance(userId: String): Flow<Double>
    fun getWithdrawableBalance(userId: String): Flow<Double>
    fun getBonusBalance(userId: String): Flow<Double>

    // Transaction methods with pagination support
    fun getTransactions(userId: String): Flow<List<Transaction>>
    suspend fun getTransactionsPaginated(
        userId: String,
        params: PaginationParams
    ): Result<PaginationResult<Transaction>>

    fun getPendingDeposits(userId: String): Flow<List<PendingDeposit>>

    suspend fun createPendingDeposit(deposit: PendingDeposit): Result<String>
    suspend fun verifyDeposit(depositId: String, adminId: String): Result<Unit>
    suspend fun rejectDeposit(depositId: String, adminId: String, reason: String): Result<Unit>

    suspend fun createTransaction(transaction: Transaction): Result<String>
    suspend fun updateTransactionStatus(transactionId: String, status: String): Result<Unit>

    suspend fun checkBalance(userId: String, amount: Double): Boolean
    suspend fun updateBalance(userId: String, amount: Double): Result<Unit>

    // Withdrawal
    suspend fun createWithdrawalRequest(request: WithdrawalRequest): Result<String>
    fun getWithdrawalRequests(userId: String): Flow<List<WithdrawalRequest>>

    // 🏆 NEW REQUIRED FUNCTION: For Tournament/Kill Prize Payout
    /**
     * Atomically increments the 'withdrawableBalance' (Prize Balance) in the user's wallet.
     * Used specifically for credit transactions like tournament winnings.
     *
     * @param userId The ID of the user whose wallet to update.
     * @param amount The positive kill prize money to add.
     */
    suspend fun incrementWithdrawableBalance(userId: String, amount: Double): Result<Unit>
}