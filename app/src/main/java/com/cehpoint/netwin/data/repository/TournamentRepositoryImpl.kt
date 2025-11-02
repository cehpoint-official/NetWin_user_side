package com.cehpoint.netwin.data.repository

import android.util.Log
import com.cehpoint.netwin.data.remote.FirebaseManager
import com.cehpoint.netwin.domain.model.RewardDistribution
import com.cehpoint.netwin.domain.model.TournamentStatus
import com.cehpoint.netwin.domain.repository.TournamentRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.cehpoint.netwin.domain.model.Tournament as DomainTournament // Alias for clarity
import com.cehpoint.netwin.data.model.TournamentRegistration // Assuming this data model exists
import com.cehpoint.netwin.data.model.Wallet // Assuming Wallet data model
import com.cehpoint.netwin.data.model.User // Assuming User data model
import com.cehpoint.netwin.data.model.Transaction // Assuming Transaction data model
import com.cehpoint.netwin.data.model.TransactionStatus // Assuming enum
import com.cehpoint.netwin.data.model.TransactionType // Assuming enum
import com.cehpoint.netwin.data.model.TeamMember // Assuming data model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date

class TournamentRepositoryImpl @Inject constructor(
    private val firebaseManager: FirebaseManager
) : TournamentRepository {

    // Reference to the main tournaments collection in Firestore
    private val tournamentsCollection = firebaseManager.firestore.collection(FirebaseManager.Companion.Collections.TOURNAMENTS)
    // Reference to the registrations sub-collection or root collection
    private val registrationsCollection = firebaseManager.firestore.collection("tournament_registrations")
    // Reference to wallets collection
    private val walletsCollection = firebaseManager.firestore.collection("wallets")
    // Reference to users collection
    private val usersCollection = firebaseManager.firestore.collection("users")
    // Reference to transactions collection (adjust if it's a subcollection)
    private val transactionsCollection = firebaseManager.firestore.collection("wallet_transactions")
    // NEW: Reference to score submissions collection
    private val scoreSubmissionsCollection = firebaseManager.firestore.collection("score_submissions")


    // Fetches featured tournaments (one-time fetch)
    override suspend fun getFeaturedTournaments(): List<DomainTournament> = try {
        Log.d("TournamentRepository", "Fetching featured tournaments...")
        val snapshot = tournamentsCollection
            .whereEqualTo("isFeatured", true) // Assuming you have an 'isFeatured' field
            .get()
            .await()
        Log.d("TournamentRepository", "Found ${snapshot.documents.size} featured tournaments")
        snapshot.documents.mapNotNull { doc ->
            mapDocumentToDomainTournament(doc.id, doc.data) // Use mapping function
        }
    } catch (e: Exception) {
        Log.e("TournamentRepository", "Error fetching featured tournaments: ${e.message}", e)
        emptyList() // Return empty list on error
    }

    // Fetches all tournaments in real-time using a Flow
    override fun getTournaments(): Flow<List<DomainTournament>> = callbackFlow {
        Log.d("TournamentRepository", "Starting real-time listener for tournaments...")

        // Listen for real-time updates, ordering by start time
        val subscription = tournamentsCollection
            .orderBy("startTime") // Order by the timestamp field
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TournamentRepository", "Error listening to tournaments: ${error.message}", error)
                    close(error) // Close the flow with error
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.w("TournamentRepository", "Received null snapshot.")
                    return@addSnapshotListener
                }

                Log.d("TournamentRepository", "Received snapshot with ${snapshot.documents.size} tournaments")

                // Perform mapping on a background thread to avoid blocking the main thread
                launch(Dispatchers.Default) {
                    val tournaments = snapshot.documents.mapNotNull { doc ->
                        try {
                            mapDocumentToDomainTournament(doc.id, doc.data) // Use mapping function
                        } catch (e: Exception) {
                            Log.e("TournamentRepository", "Error parsing tournament ${doc.id}: ${e.message}", e)
                            null // Skip documents that fail to parse
                        }
                    }
                    Log.d("TournamentRepository", "Mapped ${tournaments.size} tournaments, sending to flow...")
                    trySend(tournaments).isSuccess // Send the mapped list to the collector
                }
            }

        // Unsubscribe from the listener when the flow is cancelled
        awaitClose {
            Log.d("TournamentRepository", "Closing real-time tournament listener.")
            subscription.remove()
        }
    }

    // Fetches a single tournament by its ID (one-time fetch)
    override suspend fun getTournamentById(id: String): DomainTournament? = try {
        Log.d("TournamentRepository", "Fetching tournament by ID: $id")
        val doc = tournamentsCollection.document(id).get().await()
        mapDocumentToDomainTournament(doc.id, doc.data) // Use mapping function
    } catch (e: Exception) {
        Log.e("TournamentRepository", "Error fetching tournament by ID $id: ${e.message}", e)
        null // Return null on error
    }

    /**
     * Central helper function to safely map Firestore document data (Map<String, Any>)
     * to our DomainTournament data class.
     */
    private fun mapDocumentToDomainTournament(id: String, data: Map<String, Any>?): DomainTournament? {
        if (data == null) {
            Log.w("TournamentMapper", "Cannot map null data for ID: $id")
            return null
        }

        try {
            // Helper to safely convert Firestore Timestamp or Long to Long (milliseconds)
            fun getMillis(fieldValue: Any?): Long? {
                return when (fieldValue) {
                    is Timestamp -> fieldValue.toDate().time
                    is Number -> fieldValue.toLong() // Assume it might already be Long/Number
                    else -> null
                }
            }
            fun getMillisOrDefault(fieldValue: Any?, default: Long = 0L): Long {
                return getMillis(fieldValue) ?: default
            }


            // Safely parse reward distribution list
            val rewardsList = (data["rewardsDistribution"] as? List<*>)?.mapNotNull { rewardMap ->
                if (rewardMap is Map<*, *>) {
                    RewardDistribution(
                        position = (rewardMap["position"] as? Number)?.toInt() ?: 0,
                        // Adjust if storing 'amount' instead of 'percentage'
                        percentage = (rewardMap["percentage"] as? Number)?.toDouble() ?: 0.0
                    )
                } else null
            } ?: emptyList()

            // Map fields, providing defaults for non-nullable properties in DomainTournament
            return DomainTournament(
                id = id,
                name = data["name"] as? String ?: "", // Use 'name'
                description = data["description"] as? String ?: "",
                gameType = data["gameType"] as? String ?: "",
                matchType = data["matchType"] as? String ?: "", // Use 'matchType'
                map = data["map"] as? String ?: "",
                startTime = getMillisOrDefault(data["startTime"]), // Use helper
                entryFee = (data["entryFee"] as? Number)?.toDouble() ?: 0.0,
                prizePool = (data["prizePool"] as? Number)?.toDouble() ?: 0.0,
                maxTeams = (data["maxTeams"] as? Number)?.toInt() ?: 0, // Use 'maxTeams'
                registeredTeams = (data["registeredTeams"] as? Number)?.toInt() ?: 0,
                status = data["status"] as? String ?: "upcoming",
                rules = (data["rules"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                bannerImage = data["bannerImage"] as? String, // Use 'bannerImage'
                rewardsDistribution = rewardsList,
                createdAt = getMillisOrDefault(data["createdAt"]),
                killReward = (data["killReward"] as? Number)?.toDouble(), // Use 'killReward'
                roomId = data["roomId"] as? String,
                roomPassword = data["roomPassword"] as? String,
                actualStartTime = getMillis(data["actualStartTime"]),
                completedAt = getMillis(data["completedAt"]),
                country = data["country"] as? String,
                registrationStartTime = getMillis(data["registrationStartTime"]),
                registrationEndTime = getMillis(data["registrationEndTime"])
            )
        } catch (e: Exception) {
            Log.e("TournamentMapper", "Exception mapping document $id: ${e.message}", e)
            return null // Return null if mapping fails for any reason
        }
    }

    // Creates a new tournament document in Firestore
    override suspend fun createTournament(tournament: DomainTournament): Result<DomainTournament> = try {
        // Map domain model back to a data structure Firestore understands
        val dataTournament = mapDomainTournamentToData(tournament)
        val docRef = tournamentsCollection.add(dataTournament).await()
        // Return the created tournament with the generated ID
        Result.success(tournament.copy(id = docRef.id))
    } catch (e: Exception) {
        Log.e("TournamentRepository", "Error creating tournament: ${e.message}", e)
        Result.failure(e)
    }

    // Updates an existing tournament document in Firestore
    override suspend fun updateTournament(tournament: DomainTournament): Result<DomainTournament> = try {
        val dataTournament = mapDomainTournamentToData(tournament)
        // Use set with merge option or update specific fields as needed
        tournamentsCollection.document(tournament.id).set(dataTournament).await()
        Result.success(tournament)
    } catch (e: Exception) {
        Log.e("TournamentRepository", "Error updating tournament ${tournament.id}: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Helper to map our DomainTournament model back to a data Map suitable for Firestore.
     * Converts Long timestamps back to Firestore Timestamps.
     */
    private fun mapDomainTournamentToData(tournament: DomainTournament): Map<String, Any?> {
        fun toTimestamp(millis: Long?): Timestamp? {
            return millis?.let { Timestamp(Date(it)) }
        }

        return mapOf(
            "name" to tournament.name,
            "description" to tournament.description,
            "gameType" to tournament.gameType,
            "entryFee" to tournament.entryFee,
            "prizePool" to tournament.prizePool,
            "maxTeams" to tournament.maxTeams,
            "registeredTeams" to tournament.registeredTeams,
            "status" to tournament.status,
            "startDate" to toTimestamp(tournament.startTime), // Convert back
            "endDate" to toTimestamp(tournament.completedAt), // Convert back
            "rules" to tournament.rules,
            "bannerImage" to tournament.bannerImage,
            "rewardsDistribution" to tournament.rewardsDistribution.map {
                // Adjust if storing amount instead of percentage
                mapOf("position" to it.position, "percentage" to it.percentage)
            },
            "createdAt" to toTimestamp(tournament.createdAt), // Convert back
            "killReward" to tournament.killReward,
            "roomId" to tournament.roomId,
            "roomPassword" to tournament.roomPassword,
            "actualStartTime" to toTimestamp(tournament.actualStartTime), // Convert back
            "country" to tournament.country,
            "registrationStartTime" to toTimestamp(tournament.registrationStartTime), // Convert back
            "registrationEndTime" to toTimestamp(tournament.registrationEndTime) // Convert back
            // Add any other fields that need to be saved
        ).filterValues { it != null } // Optionally remove null values if Firestore rules require it
    }


    // Deletes a tournament document
    override suspend fun deleteTournament(id: String): Result<Unit> = try {
        tournamentsCollection.document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // --- (joinTournament and leaveTournament might be handled by registerForTournament logic or Cloud Functions) ---
    // Placeholder implementations if needed directly
    override suspend fun joinTournament(tournamentId: String, userId: String): Result<Unit> {
        Log.w("TournamentRepository", "joinTournament function is likely deprecated, use registerForTournament.")
        // This should ideally be part of the registration transaction or a Cloud Function
        return Result.failure(UnsupportedOperationException("Use registerForTournament"))
    }

    override suspend fun leaveTournament(tournamentId: String, userId: String): Result<Unit> {
        Log.w("TournamentRepository", "leaveTournament function needs implementation (e.g., delete registration, update counts via transaction/Cloud Function).")
        // Requires deleting registration and potentially updating counts in a transaction
        return Result.failure(UnsupportedOperationException("leaveTournament not fully implemented"))
    }


    // Updates only the status field of a tournament
    override suspend fun updateTournamentStatus(id: String, status: TournamentStatus): Result<Unit> = try {
        tournamentsCollection.document(id)
            .update("status", status.name.uppercase()) // Store status consistently (e.g., uppercase)
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("TournamentRepository", "Error updating status for $id: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Fetches the list of tournament IDs that the user has registered for in real-time.
     * The return type is Flow<List<String>>.
     * This implementation is CORRECT as provided in your input.
     */
    override fun getUserTournamentRegistrations(userId: String): Flow<List<String>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList()).isSuccess
            close(Exception("User ID cannot be blank for registration flow."))
            return@callbackFlow
        }

        Log.d("TournamentRepository", "Starting real-time listener for user registrations: $userId")

        // Listen for real-time updates on registrations for the current user
        val subscription = registrationsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("TournamentRepository", "Error listening to user $userId registrations: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.w("TournamentRepository", "Received null registration snapshot for user $userId.")
                    return@addSnapshotListener
                }

                // Map the results to a list of tournament IDs
                val ids = snapshot.documents.mapNotNull { doc -> doc.getString("tournamentId") }
                Log.d("TournamentRepository", "Real-time update: User $userId registered for: $ids")
                trySend(ids).isSuccess
            }

        // Unsubscribe from the listener when the flow is cancelled
        awaitClose {
            Log.d("TournamentRepository", "Closing real-time user registration listener for $userId.")
            subscription.remove()
        }
    }

    // Checks if a specific user is registered for a specific tournament (Kept as suspend/one-time check)
    override suspend fun isUserRegisteredForTournament(tournamentId: String, userId: String): Boolean = try {
        // Use the specific document ID format for quick check
        val docId = "${userId}_${tournamentId}" // Corrected docId format: it should match the one used in registerForTournament
        registrationsCollection.document(docId).get().await().exists()
    } catch (e: Exception) {
        Log.e("TournamentRepository", "Error checking registration status for user $userId, tournament $tournamentId: ${e.message}", e)
        false
    }

    // Handles the complex process of registering a user for a tournament using a Firestore transaction
    override suspend fun registerForTournament(
        tournamentId: String,
        userId: String,
        displayName: String, // User's display name for team info
        teamName: String, // Team name chosen by user
        playerIds: List<String> // In-game IDs of players
    ): Result<Unit> = try {
        Log.d("TournamentRepoImpl", "Attempting registration: User=$userId, Tourn=$tournamentId")
        val tournamentRef = tournamentsCollection.document(tournamentId)
        val userWalletRef = walletsCollection.document(userId)
        // Use the combined ID for the registration document
        val registrationRef = registrationsCollection.document("${userId}_${tournamentId}")
        val userRef = usersCollection.document(userId)

        firebaseManager.firestore.runTransaction { transaction ->
            // 1. Read necessary documents within the transaction
            Log.d("TournamentRepoImpl", "[TXN] Reading documents...")
            val tournamentSnapshot = transaction.get(tournamentRef)
            val walletSnapshot = transaction.get(userWalletRef)
            val registrationSnapshot = transaction.get(registrationRef) // Check if already registered
            val userSnapshot = transaction.get(userRef)

            // 2. Deserialize and Validate Data
            Log.d("TournamentRepoImpl", "[TXN] Deserializing and validating...")
            val tournament = mapDocumentToDomainTournament(tournamentSnapshot.id, tournamentSnapshot.data)
                ?: throw Exception("Tournament not found (ID: $tournamentId).")

            // Revert to direct access for objects not provided
            val walletMap = walletSnapshot.data
                ?: throw Exception("User wallet not found (ID: $userId).")
            val bonusBalance = (walletMap["bonusBalance"] as? Number)?.toDouble() ?: 0.0
            val withdrawableBalance = (walletMap["withdrawableBalance"] as? Number)?.toDouble() ?: 0.0
            val currency = walletMap["currency"] as? String ?: "INR"

            val userMap = userSnapshot.data
                ?: throw Exception("User profile not found (ID: $userId).")
            val kycStatus = userMap["kycStatus"] as? String

            // --- Validation Checks ---
            if (registrationSnapshot.exists()) {
                throw Exception("You are already registered for this tournament.")
            }
            // Use calculated properties from the domain model for checks
            if (tournament.isFull) {
                throw Exception("Sorry, this tournament is already full (${tournament.registeredTeams}/${tournament.maxTeams}).")
            }
            if (!tournament.isRegistrationWindowOpen) {
                throw Exception("Registration is currently closed for this tournament.")
            }
            val totalBalance = bonusBalance + withdrawableBalance
            if (totalBalance < tournament.entryFee) {
                throw Exception("Insufficient balance (${totalBalance} < ${tournament.entryFee}). Please add funds.")
            }
            if (tournament.entryFee > 0 && kycStatus?.equals("verified", ignoreCase = true) == false) {
                throw Exception("KYC verification is required to join paid tournaments.")
            }
            Log.d("TournamentRepoImpl", "[TXN] Validation passed.")

            // 3. Perform Writes within the transaction
            Log.d("TournamentRepoImpl", "[TXN] Performing writes...")
            val entryFee = tournament.entryFee
            val bonusUsed = minOf(bonusBalance, entryFee)
            val withdrawableUsed = entryFee - bonusUsed
            val newBonusBalance = bonusBalance - bonusUsed
            val newWithdrawableBalance = withdrawableBalance - withdrawableUsed

            // Update wallet balances
            transaction.update(userWalletRef, mapOf(
                "bonusBalance" to newBonusBalance,
                "withdrawableBalance" to newWithdrawableBalance,
                "balance" to newBonusBalance + newWithdrawableBalance // Update total balance too
            ))
            Log.d("TournamentRepoImpl", "[TXN] Wallet updated.")

            // Create Registration Document
            val teamMembers = playerIds.mapIndexed { index, inGameId ->
                // Use provided display name for the first player, generic for others
                val username = if (index == 0) displayName else "Player ${index + 1}"
                TeamMember(username = username, inGameId = inGameId)
            }
            val registration = TournamentRegistration(
                tournamentId = tournamentId,
                userId = userId,
                teamName = teamName.ifBlank { "$displayName's Team" }, // Default team name if empty
                teamMembers = teamMembers,
                paymentStatus = "completed", // Assume payment is part of this transaction
                registeredAt = Timestamp.now() // Use server timestamp
            )
            transaction.set(registrationRef, registration)
            Log.d("TournamentRepoImpl", "[TXN] Registration document created.")

            // Create Wallet Transaction Log
            val transactionDescription = "Entry Fee: ${tournament.name}"
            val walletTransaction = Transaction(
                userId = userId,
                type = TransactionType.ENTRY_FEE,
                amount = entryFee,
                currency = currency, // Use wallet currency
                status = TransactionStatus.COMPLETED,
                description = transactionDescription,
                createdAt = Timestamp.now(), // Use server timestamp
                tournamentId = tournamentId // Link transaction to tournament
            )
            // Create a *new* document in the transactions collection
            val newTransactionRef = transactionsCollection.document()
            transaction.set(newTransactionRef, walletTransaction)
            Log.d("TournamentRepoImpl", "[TXN] Wallet transaction logged.")

            // --- IMPORTANT: registeredTeams Count Update ---
            // It's generally better to update counts using Cloud Functions triggered by
            // new registrations to avoid contention and permission issues.
            // If you MUST do it here, uncomment the line below, but ensure security rules allow it.
            // transaction.update(tournamentRef, "registeredTeams", FieldValue.increment(1))
            Log.w("TournamentRepoImpl", "[TXN] Skipping direct registeredTeams increment. Use Cloud Functions.")

            // Transaction completes automatically if no exceptions are thrown
            Log.d("TournamentRepoImpl", "[TXN] Transaction successful.")
            null // Return null for successful transaction commit in Kotlin SDK
        }.await() // Wait for the transaction to complete

        Log.i("TournamentRepoImpl", "Registration successful for User=$userId, Tourn=$tournamentId")
        Result.success(Unit)
    } catch (e: Exception) {
        // Log specific errors for debugging
        Log.e("TournamentRepoImpl", "Registration failed for User=$userId, Tourn=$tournamentId: ${e.message}", e)
        // Provide user-friendly error messages
        val userMessage = when {
            e.message?.contains("already registered", ignoreCase = true) == true -> "You are already registered."
            e.message?.contains("full", ignoreCase = true) == true -> "Tournament is full."
            e.message?.contains("closed", ignoreCase = true) == true -> "Registration is closed."
            e.message?.contains("Insufficient balance", ignoreCase = true) == true -> "Insufficient balance."
            e.message?.contains("KYC", ignoreCase = true) == true -> "KYC verification needed."
            e.message?.contains("not found", ignoreCase = true) == true -> "Tournament or user data not found. Please try again later."
            else -> "Registration failed. Please try again." // Generic error
        }
        Result.failure(Exception(userMessage, e)) // Wrap original exception
    }

    /**
     * Logs a user's score submission (e.g., via screenshot) to the database for review.
     * This corresponds to the "Scan and Earn" feature when a match is ongoing.
     *
     * Note: This is a placeholder. A real implementation would include a screenshot URL.
     *
     * @param tournamentId The ID of the tournament.
     * @param userId The ID of the submitting user.
     * @return Result<Unit> indicating success or failure of logging the submission.
     */
    suspend fun logScoreSubmission(tournamentId: String, userId: String, screenshotUrl: String?): Result<Unit> = try {
        Log.d("TournamentRepoImpl", "Logging score submission for User=$userId, Tourn=$tournamentId")

        val submissionData = hashMapOf(
            "userId" to userId,
            "tournamentId" to tournamentId,
            "submissionType" to "screenshot",
            "screenshotUrl" to (screenshotUrl ?: "URL_PENDING_UPLOAD"), // Placeholder for the actual URL
            "submittedAt" to Timestamp.now(),
            "status" to "pending_review" // Requires admin review
        )

        scoreSubmissionsCollection.add(submissionData).await()
        Log.i("TournamentRepoImpl", "Score submission logged successfully.")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e("TournamentRepoImpl", "Failed to log score submission: ${e.message}", e)
        Result.failure(Exception("Failed to submit score. Please check your network.", e))
    }
}