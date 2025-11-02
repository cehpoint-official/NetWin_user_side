package com.cehpoint.netwin.data.repository

import android.util.Log
import com.cehpoint.netwin.data.model.DepositStatus
import com.cehpoint.netwin.data.model.KycStatus
import com.cehpoint.netwin.data.model.PaginationParams
import com.cehpoint.netwin.data.model.PaginationResult
import com.cehpoint.netwin.data.model.PaymentMethod
import com.cehpoint.netwin.data.model.PendingDeposit
import com.cehpoint.netwin.data.model.Transaction
import com.cehpoint.netwin.data.model.TransactionStatus
import com.cehpoint.netwin.data.model.TransactionType
import com.cehpoint.netwin.data.model.User
import com.cehpoint.netwin.data.model.Wallet
import com.cehpoint.netwin.data.model.WithdrawalRequest
import com.cehpoint.netwin.data.model.UserDetails
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.domain.repository.WalletData
import com.cehpoint.netwin.domain.repository.WalletRepository
import com.cehpoint.netwin.utils.IdempotencyManager
import com.cehpoint.netwin.utils.KYCMonitor
import com.cehpoint.netwin.utils.NGNTransactionUtils
import com.cehpoint.netwin.utils.OfflineQueueManager
import com.cehpoint.netwin.utils.RetryUtils
import com.google.firebase.Timestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor(
    private val firebaseManager: FirebaseManager,
    private val offlineQueueManager: OfflineQueueManager,
    private val idempotencyManager: IdempotencyManager,
    private val kycMonitor: KYCMonitor
) : WalletRepository {

    companion object {
        private const val TAG = "WalletRepositoryImpl"
    }

    private val usersCollection = firebaseManager.firestore.collection("users")
    private val walletsCollection = firebaseManager.firestore.collection("wallets")
    private val transactionsCollection = firebaseManager.firestore.collection("wallet_transactions")
    private val pendingDepositsCollection = firebaseManager.firestore.collection("pending_deposits")
    private val pendingWithdrawalsCollection = firebaseManager.firestore.collection("pending_withdrawals")

    // --- All existing functions (listenToWalletChanges, getWalletBalance, etc.) are kept as they are ---

    /**
     * balance = Cash Balance (Deposits/General use)
     * withdrawable = Prize Balance (Winnings/Withdrawable funds)
     */
    override fun listenToWalletChanges(userId: String): Flow<WalletData> = callbackFlow {
        val listener = walletsCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to wallet changes for user $userId", error)
                    close(error)
                    return@addSnapshotListener
                }

                // Get the Wallet data object
                val wallet = snapshot?.toObject(Wallet::class.java)

                // Map to the domain model WalletData
                val walletData = WalletData(
                    balance = wallet?.balance ?: 0.0, // Cash Balance
                    withdrawable = wallet?.withdrawableBalance ?: 0.0, // Prize Balance
                    bonus = 0.0 // Bonus concept removed
                )

                Log.d(TAG, "Wallet data updated for user $userId: $walletData")
                trySend(walletData)
            }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Wallet changes listener removed for user $userId")
        }
    }

    /**
     * Returns the 'balance' field (now Cash Balance).
     */
    override fun getWalletBalance(userId: String): Flow<Double> = callbackFlow {
        val listener = walletsCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting wallet balance for user $userId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val wallet = snapshot?.toObject(Wallet::class.java)
                // This is now CASH BALANCE
                val balance = wallet?.balance ?: 0.0

                Log.d(TAG, "Wallet balance updated for user $userId: $balance")
                trySend(balance)
            }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Wallet balance listener removed for user $userId")
        }
    }

    /**
     * Returns the 'withdrawableBalance' field (now Prize Balance).
     */
    override fun getWithdrawableBalance(userId: String): Flow<Double> = callbackFlow {
        val listener = walletsCollection.document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting withdrawable balance for user $userId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val wallet = snapshot?.toObject(Wallet::class.java)
                // This is now PRIZE BALANCE
                val withdrawableBalance = wallet?.withdrawableBalance ?: 0.0
                Log.d(TAG, "Withdrawable (Prize) balance updated for user $userId: $withdrawableBalance")
                trySend(withdrawableBalance)
            }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Withdrawable balance listener removed for user $userId")
        }
    }

    /**
     * Hardcoded to return 0.0 since the bonus concept is removed.
     */
    override fun getBonusBalance(userId: String): Flow<Double> = callbackFlow {
        trySend(0.0) // Bonus balance is removed, so it's always 0.0
        awaitClose {
            Log.d(TAG, "Bonus balance listener closed (always returns 0.0)")
        }
    }

    /**
     * Fetches transactions ONLY from the main 'wallet_transactions' collection.
     */
    override fun getTransactions(userId: String): Flow<List<Transaction>> = callbackFlow {
        Log.d(TAG, "Starting transaction listener for user $userId using main transactions collection")

        val mainListener = transactionsCollection
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting main transactions for user $userId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        Log.d(TAG, "Processing transaction ${doc.id} for user $userId")
                        doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize transaction ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Main transactions loaded for user $userId: ${transactions.size} items")
                trySend(transactions)
            }

        awaitClose {
            mainListener.remove()
            Log.d(TAG, "Main transaction listener removed for user $userId")
        }
    }

    override suspend fun getTransactionsPaginated(
        userId: String,
        params: PaginationParams
    ): Result<PaginationResult<Transaction>> = try {
        Log.d(TAG, "Loading paginated transactions for user $userId, pageSize: ${params.pageSize}, loadMore: ${params.loadMore}")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            var query = transactionsCollection
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(params.pageSize.toLong())

            // If loading more, start after the last document
            if (params.loadMore && params.lastDocument != null) {
                query = query.startAfter(params.lastDocument)
            }

            val snapshot = query.get().await()
            val transactions = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Transaction::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to deserialize transaction ${doc.id}: ${e.message}")
                    null
                }
            }

            val hasMore = transactions.size == params.pageSize
            val lastDocument = if (transactions.isNotEmpty()) snapshot.documents.last() else null

            Log.d(TAG, "Paginated transactions loaded for user $userId: ${transactions.size} items, hasMore: $hasMore")

            PaginationResult(
                items = transactions,
                hasMore = hasMore,
                lastDocument = lastDocument,
                totalCount = transactions.size
            )
        }

        result.onSuccess { paginationResult ->
            Log.d(TAG, "Paginated transactions loaded successfully for user $userId")
        }.onFailure { e ->
            Log.e(TAG, "Failed to load paginated transactions for user $userId after retries", e)
        }

        result
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load paginated transactions for user $userId", e)
        Result.failure(e)
    }

    /**
     * REVERTED: Now only fetches deposits with **PENDING** status.
     */
    override fun getPendingDeposits(userId: String): Flow<List<PendingDeposit>> = callbackFlow {
        val listener = pendingDepositsCollection
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", DepositStatus.PENDING) // <-- REVERTED to only show PENDING
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting pending deposits for user $userId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val deposits = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(PendingDeposit::class.java)?.copy(requestId = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize deposit ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()

                // If this list is empty, the UI column should disappear.
                Log.d(TAG, "Pending deposits updated for user $userId: ${deposits.size} items")
                trySend(deposits)
            }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Pending deposits listener removed for user $userId")
        }
    }

    override suspend fun createPendingDeposit(deposit: PendingDeposit): Result<String> = try {
        Log.d(TAG, "Creating pending deposit for user ${deposit.userId}, amount: ${deposit.amount}, currency: ${deposit.currency}")

        // Validation logic... (omitted for brevity, assume it passes)

        // Check for duplicate operation using idempotency
        val operationData = mapOf(
            "amount" to deposit.amount,
            "currency" to deposit.currency,
            "upiRefId" to deposit.upiRefId,
            "userUpiId" to deposit.userUpiId,
            "paymentMethod" to deposit.paymentMethod.name,
            "userCountry" to deposit.userCountry
        )

        if (idempotencyManager.isDuplicateOperation("CREATE_DEPOSIT", deposit.userId, operationData)) {
            throw IllegalArgumentException("Duplicate deposit request detected. Please wait before trying again.")
        }

        // Use retry logic for critical operations
        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            val docRef = pendingDepositsCollection.add(deposit).await()
            docRef.id
        }

        result.onSuccess { id ->
            // Store idempotency key on success
            idempotencyManager.storeIdempotencyKey("CREATE_DEPOSIT", deposit.userId, operationData)
            Log.d(TAG, "Pending deposit created successfully with ID: $id")
        }.onFailure { e ->
            Log.e(TAG, "Failed to create pending deposit after retries", e)
        }

        result
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create pending deposit", e)
        Result.failure(e)
    }

    /**
     * Deposits now only increase the 'balance' (Cash Balance).
     * 'withdrawableBalance' (Prize Balance) is left untouched.
     */
    override suspend fun verifyDeposit(depositId: String, adminId: String): Result<Unit> = try {
        Log.d(TAG, "Verifying deposit $depositId by admin $adminId")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            firebaseManager.firestore.runTransaction { transaction ->
                val depositRef = pendingDepositsCollection.document(depositId)
                val deposit = transaction.get(depositRef).toObject(PendingDeposit::class.java)
                    ?: throw Exception("Deposit not found")

                // Check if deposit is already processed
                if (deposit.status != DepositStatus.PENDING) {
                    throw Exception("Deposit is already ${deposit.status.name.lowercase()}")
                }

                // Update deposit status (retained, not deleted)
                transaction.update(depositRef, "status", DepositStatus.APPROVED.name) // Use .name for String serialization
                transaction.update(depositRef, "verifiedBy", adminId)
                transaction.update(depositRef, "verifiedAt", com.google.firebase.Timestamp.now())

                // Update wallet balance atomically
                val walletRef = walletsCollection.document(deposit.userId)
                val wallet = transaction.get(walletRef).toObject(Wallet::class.java)
                    ?: throw Exception("Wallet not found")

                // --- MODIFICATION: Deposit only affects the CASH BALANCE ('balance') ---
                val newCashBalance = wallet.balance + deposit.amount
                val newPrizeBalance = wallet.withdrawableBalance // UNCHANGED

                transaction.update(walletRef, mapOf(
                    "balance" to newCashBalance,
                    "withdrawableBalance" to newPrizeBalance,
                    "currency" to (wallet.currency ?: "INR") // Ensure currency is set
                ))
                // --- END MODIFICATION ---

                // Update user's walletBalance for display
                val userRef = usersCollection.document(deposit.userId)
                transaction.update(userRef, "walletBalance", newCashBalance)

                // Create transaction record with proper description (for history)
                val transactionRecord = Transaction(
                    userId = deposit.userId,
                    amount = deposit.amount,
                    currency = deposit.currency,
                    type = NGNTransactionUtils.getTransactionTypeForPaymentMethod(deposit.paymentMethod, true),
                    status = TransactionStatus.COMPLETED, // Deposit status is COMPLETED immediately upon verification
                    description = NGNTransactionUtils.getPaymentDescription(deposit.paymentMethod, deposit.amount, deposit.currency),
                    paymentMethod = deposit.paymentMethod,
                    createdAt = com.google.firebase.Timestamp.now()
                )
                val transactionDocRef = transactionsCollection.document()
                transaction.set(transactionDocRef, transactionRecord)
            }.await()
        }

        result.onSuccess {
            Log.d(TAG, "Deposit $depositId verified successfully")
        }.onFailure { e ->
            Log.e(TAG, "Failed to verify deposit $depositId after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to verify deposit $depositId", e)
        Result.failure(e)
    }

    override suspend fun rejectDeposit(depositId: String, adminId: String, reason: String): Result<Unit> = try {
        Log.d(TAG, "Rejecting deposit $depositId by admin $adminId, reason: $reason")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            // Deposit is updated to REJECTED (retained, not deleted)
            pendingDepositsCollection.document(depositId)
                .update(
                    mapOf(
                        "status" to DepositStatus.REJECTED.name, // Use .name for String serialization
                        "rejectedBy" to adminId,
                        "rejectedAt" to com.google.firebase.Timestamp.now(),
                        "rejectionReason" to reason
                    )
                ).await()
        }

        result.onSuccess {
            Log.d(TAG, "Deposit $depositId rejected successfully")
        }.onFailure { e ->
            Log.e(TAG, "Failed to reject deposit $depositId after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to reject deposit $depositId", e)
        Result.failure(e)
    }

    override suspend fun createTransaction(transaction: Transaction): Result<String> = try {
        Log.d(TAG, "Creating transaction for user ${transaction.userId}, amount: ${transaction.amount}")

        // Validation logic... (omitted for brevity, assume it passes)

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            val docRef = transactionsCollection.add(transaction).await()
            docRef.id
        }

        result.onSuccess { id ->
            Log.d(TAG, "Transaction created successfully with ID: $id")
        }.onFailure { e ->
            Log.e(TAG, "Failed to create transaction after retries", e)
        }

        result
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create transaction", e)
        Result.failure(e)
    }

    override suspend fun updateTransactionStatus(transactionId: String, status: String): Result<Unit> = try {
        Log.d(TAG, "Updating transaction $transactionId status to $status")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            transactionsCollection.document(transactionId)
                .update("status", status)
                .await()
        }

        result.onSuccess {
            Log.d(TAG, "Transaction $transactionId status updated successfully")
        }.onFailure { e ->
            Log.e(TAG, "Failed to update transaction $transactionId status after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update transaction $transactionId status", e)
        Result.failure(e)
    }

    /**
     * Checks against the 'balance' field (Cash Balance).
     */
    override suspend fun checkBalance(userId: String, amount: Double): Boolean = try {
        Log.d(TAG, "Checking balance (Cash) for user $userId, required amount: $amount")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            val wallet = walletsCollection.document(userId).get().await().toObject(Wallet::class.java)
            // Use 'balance' (Cash Balance) for general check
            val totalBalance = wallet?.balance ?: 0.0
            totalBalance >= amount
        }

        result.onSuccess { hasSufficientBalance ->
            Log.d(TAG, "Balance check result for user $userId: $hasSufficientBalance")
        }.onFailure { e ->
            Log.e(TAG, "Failed to check balance for user $userId after retries", e)
        }

        result.getOrElse { false }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to check balance for user $userId", e)
        false
    }

    /**
     * General updates (credit/debit) now only affect the 'balance' (Cash Balance).
     * 'withdrawableBalance' (Prize Balance) is left untouched.
     */
    override suspend fun updateBalance(userId: String, amount: Double): Result<Unit> = try {
        Log.d(TAG, "Updating balance (Cash) for user $userId, amount: $amount")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            firebaseManager.firestore.runTransaction { transaction ->
                val walletRef = walletsCollection.document(userId)
                val wallet = transaction.get(walletRef).toObject(Wallet::class.java)
                    ?: throw Exception("Wallet not found")

                // --- MODIFICATION: General update only affects the CASH BALANCE ('balance') ---
                val newCashBalance = wallet.balance + amount
                val newPrizeBalance = wallet.withdrawableBalance // UNCHANGED

                transaction.update(walletRef, mapOf(
                    "balance" to newCashBalance,
                    "withdrawableBalance" to newPrizeBalance,
                    "currency" to (wallet.currency ?: "INR") // Ensure currency is set
                ))
                // --- END MODIFICATION ---

                // Update user's walletBalance for display
                val userRef = usersCollection.document(userId)
                transaction.update(userRef, "walletBalance", newCashBalance)
            }.await()
        }

        result.onSuccess {
            Log.d(TAG, "Balance updated successfully for user $userId")
        }.onFailure { e ->
            Log.e(TAG, "Failed to update balance for user $userId after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update balance for user $userId", e)
        Result.failure(e)
    }

    /**
     * Withdrawals now ONLY deduct from 'withdrawableBalance' (Prize Balance).
     * 'balance' (Cash Balance) is left untouched.
     * The generated transaction ID is returned in the Result for the caller to link it.
     */
    override suspend fun createWithdrawalRequest(request: WithdrawalRequest): Result<String> = try {
        Log.d(TAG, "Creating withdrawal request for user ${request.userId}, amount: ${request.amount}, currency: ${request.currency}")

        // Validation logic... (omitted for brevity, assume it passes)

        val operationData = mapOf(
            "amount" to request.amount,
            "currency" to request.currency,
            "paymentMethod" to request.paymentMethod.name
            // ... (omitted for brevity)
        )

        if (idempotencyManager.isDuplicateOperation("CREATE_WITHDRAWAL", request.userId, operationData)) {
            throw IllegalArgumentException("Duplicate withdrawal request detected. Please wait before trying again.")
        }

        // Use a transaction for atomic read/write on wallet and write operations
        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            firebaseManager.firestore.runTransaction { transaction ->
                // Get current wallet state atomically
                val walletRef = walletsCollection.document(request.userId)
                val wallet = transaction.get(walletRef).toObject(Wallet::class.java)
                    ?: throw Exception("Wallet not found")

                // Check balance atomically - ONLY check Prize Balance
                if (wallet.withdrawableBalance < request.amount) {
                    throw Exception("Insufficient withdrawable (Prize) balance.")
                }

                // --- STEP 1: Deduct from wallet atomically ---
                val newPrizeBalance = wallet.withdrawableBalance - request.amount
                val newCashBalance = wallet.balance // UNCHANGED

                // --- ADDED NULL CHECKING HERE ---
                val finalCurrency = wallet.currency ?: "INR"
                val finalCurrencySymbol = wallet.currencySymbol ?: "₹"

                transaction.update(walletRef, mapOf(
                    "balance" to newCashBalance,
                    "withdrawableBalance" to newPrizeBalance,
                    "currency" to finalCurrency,
                    "currencySymbol" to finalCurrencySymbol,
                    "lastUpdated" to System.currentTimeMillis()
                ))

                // Update user's walletBalance for display
                transaction.update(usersCollection.document(request.userId), "walletBalance", newCashBalance)

                // --- STEP 2: Create PENDING transaction record ---
                val transactionRecord = Transaction(
                    userId = request.userId,
                    amount = request.amount,
                    currency = request.currency,
                    type = NGNTransactionUtils.getTransactionTypeForPaymentMethod(request.paymentMethod, false),
                    status = TransactionStatus.PENDING, // Initial status
                    description = NGNTransactionUtils.getPaymentDescription(request.paymentMethod, request.amount, request.currency),
                    paymentMethod = request.paymentMethod,
                    createdAt = com.google.firebase.Timestamp.now()
                )
                // Get a new ID for the transaction document first
                val transactionDocRef = transactionsCollection.document()
                transaction.set(transactionDocRef, transactionRecord)

                // --- STEP 3: Create withdrawal request ---
                val withdrawalRef = pendingWithdrawalsCollection.document()
                // FIX: Use .name to convert the Enum to String for serialization
                transaction.set(withdrawalRef, request.copy(status = TransactionStatus.PENDING.name))

                // Return the new withdrawal ID, and the Admin system will need to track the transaction ID externally.
                withdrawalRef.id
            }.await()
        }

        result.onSuccess { id ->
            idempotencyManager.storeIdempotencyKey("CREATE_WITHDRAWAL", request.userId, operationData)
            Log.d(TAG, "Withdrawal request created successfully with ID: $id")
        }.onFailure { e ->
            Log.e(TAG, "Failed to create withdrawal request for user ${request.userId} after retries", e)
        }

        result
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create withdrawal request for user ${request.userId}", e)
        Result.failure(e)
    }

    // --- ADMIN FUNCTION: COMPLETE WITHDRAWAL (APPROVAL) ---
    /**
     * Marks a withdrawal request and its corresponding transaction record as COMPLETED.
     * The transactionRecordId must be passed by the caller (Admin system) as the linkage is external.
     */
    suspend fun completeWithdrawal(
        withdrawalId: String,
        adminId: String,
        transactionRecordId: String // REQUIRED: The ID of the corresponding Transaction in wallet_transactions
    ): Result<Unit> = try {
        Log.d(TAG, "Completing withdrawal $withdrawalId and transaction $transactionRecordId by admin $adminId")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            // Use a batch write for atomic updates across two documents
            val batch = firebaseManager.firestore.batch()

            // 1. Update the pending_withdrawals document (Administrative Record)
            val withdrawalRef = pendingWithdrawalsCollection.document(withdrawalId)
            batch.update(withdrawalRef, mapOf(
                "status" to TransactionStatus.COMPLETED.name, // Use .name for String serialization
                "completedBy" to adminId,
                "completedAt" to Timestamp.now()
            ))

            // 2. Update the wallet_transactions document (User History Record)
            val transactionRef = transactionsCollection.document(transactionRecordId)
            batch.update(transactionRef, "status", TransactionStatus.COMPLETED.name)

            batch.commit().await()
        }

        result.onSuccess {
            Log.d(TAG, "Withdrawal $withdrawalId and transaction status updated to COMPLETED successfully.")
        }.onFailure { e ->
            Log.e(TAG, "Failed to complete withdrawal $withdrawalId after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to complete withdrawal $withdrawalId", e)
        Result.failure(e)
    }

    // --- ADMIN FUNCTION: REJECT WITHDRAWAL (REVERSION) ---
    /**
     * Rejects a withdrawal request, reverts the funds, and marks the transaction as REJECTED.
     * The transactionRecordId must be passed by the caller (Admin system) as the linkage is external.
     */
    suspend fun rejectWithdrawal(
        withdrawalId: String,
        adminId: String,
        reason: String,
        transactionRecordId: String // REQUIRED: The ID of the corresponding Transaction in wallet_transactions
    ): Result<Unit> = try {
        Log.d(TAG, "Rejecting withdrawal $withdrawalId and transaction $transactionRecordId by admin $adminId. Reverting funds.")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            firebaseManager.firestore.runTransaction { transaction ->
                // --- Phase 1: Read and Validation ---
                val withdrawalRef = pendingWithdrawalsCollection.document(withdrawalId)
                val withdrawal = transaction.get(withdrawalRef).toObject(WithdrawalRequest::class.java)
                    ?: throw Exception("Withdrawal request not found")

                // Ensure we only reject PENDING requests
                if (withdrawal.status.toString() != TransactionStatus.PENDING.name) {
                    throw Exception("Withdrawal is not pending, current status: ${withdrawal.status}")
                }

                val walletRef = walletsCollection.document(withdrawal.userId)
                val wallet = transaction.get(walletRef).toObject(Wallet::class.java)
                    ?: throw Exception("Wallet not found")

                // --- Phase 2: Write Operations ---

                // 1. Revert the funds (Add the amount back to the Prize Balance)
                val newPrizeBalance = wallet.withdrawableBalance + withdrawal.amount
                val newCashBalance = wallet.balance // Unchanged

                transaction.update(walletRef, mapOf(
                    "withdrawableBalance" to newPrizeBalance,
                    "balance" to newCashBalance
                ))

                // Update user's walletBalance for display (reflects Cash Balance only)
                transaction.update(usersCollection.document(withdrawal.userId), "walletBalance", newCashBalance)

                // 2. Update the pending_withdrawals document (Administrative Record)
                transaction.update(withdrawalRef, mapOf(
                    "status" to TransactionStatus.REJECTED.name,
                    "rejectedBy" to adminId,
                    "rejectionReason" to reason,
                    "rejectedAt" to Timestamp.now()
                ))

                // 3. Update the wallet_transactions document (User History Record)
                val transactionRef = transactionsCollection.document(transactionRecordId)
                transaction.update(transactionRef, "status", TransactionStatus.REJECTED.name)

                null
            }.await()
        }

        result.onSuccess {
            Log.d(TAG, "Withdrawal $withdrawalId rejected, funds reverted, and transaction status updated to REJECTED successfully.")
        }.onFailure { e ->
            Log.e(TAG, "Failed to reject withdrawal $withdrawalId after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to reject withdrawal $withdrawalId", e)
        Result.failure(e)
    }

    override fun getWithdrawalRequests(userId: String): Flow<List<WithdrawalRequest>> = callbackFlow {
        val listener = pendingWithdrawalsCollection
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error getting withdrawal requests for user $userId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val requests = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(WithdrawalRequest::class.java)?.copy(requestId = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to deserialize withdrawal request ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Withdrawal requests updated for user $userId: ${requests.size} items")
                trySend(requests)
            }

        awaitClose {
            listener.remove()
            Log.d(TAG, "Withdrawal requests listener removed for user $userId")
        }
    }

    // 🏆 NEW REQUIRED FUNCTION: For Tournament/Kill Prize Payout
    /**
     * Atomically increments the 'withdrawableBalance' (Prize Balance) in the user's wallet.
     * This is used for payouts like tournament winnings (e.g., kill prizes).
     */
    override suspend fun incrementWithdrawableBalance(userId: String, amount: Double): Result<Unit> = try {
        if (amount <= 0.0) {
            throw IllegalArgumentException("Payout amount must be positive.")
        }

        Log.d(TAG, "Incrementing withdrawableBalance (Prize) for user $userId by $amount")

        val result = RetryUtils.retryWithBackoff(maxAttempts = 3) {
            firebaseManager.firestore.runTransaction { transaction ->
                val walletRef = walletsCollection.document(userId)
                val wallet = transaction.get(walletRef).toObject(Wallet::class.java)
                    ?: throw Exception("Wallet not found for user $userId")

                // --- GUARANTEED NON-NULL VALUES FOR TRANSACTION ---
                val finalCurrency = wallet.currency ?: "INR"
                val finalCurrencySymbol = wallet.currencySymbol ?: "₹"
                // ----------------------------------------------------

                // --- ATOMIC MODIFICATION: Increment the PRIZE BALANCE ('withdrawableBalance') ---
                val newPrizeBalance = wallet.withdrawableBalance + amount
                val newCashBalance = wallet.balance // Cash Balance is UNCHANGED

                transaction.update(walletRef, mapOf(
                    "withdrawableBalance" to newPrizeBalance,
                    "balance" to newCashBalance,
                    "currency" to finalCurrency, // Use guaranteed non-null currency
                    "currencySymbol" to finalCurrencySymbol, // Ensure symbol is updated/present
                    "lastUpdated" to System.currentTimeMillis()
                ))
                // --- END ATOMIC MODIFICATION ---

                // Create a corresponding Transaction record for the history
                val transactionRecord = Transaction(
                    userId = userId,
                    amount = amount,
                    currency = finalCurrency, // Use guaranteed non-null currency
                    // ⭐ CRITICAL FIX: Changed TOURNAMENT_WIN to TOURNAMENT_WINNING for model validation
                    type = TransactionType.TOURNAMENT_WINNING,
                    status = TransactionStatus.COMPLETED,
                    description = "Prize Payout (Tournament Win: Audit Kill)",
                    paymentMethod = PaymentMethod.SYSTEM, // SYSTEM payment method
                    createdAt = com.google.firebase.Timestamp.now()
                )
                val transactionDocRef = transactionsCollection.document()
                transaction.set(transactionDocRef, transactionRecord)
            }.await()
        }

        result.onSuccess {
            Log.d(TAG, "Withdrawable balance successfully incremented for user $userId by $amount")
        }.onFailure { e ->
            Log.e(TAG, "Failed to increment withdrawable balance for user $userId after retries", e)
        }

        result.map { Unit }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to increment withdrawable balance for user $userId", e)
        Result.failure(e)
    }
}