package com.cehpoint.netwin.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class User(
    // ⭐ FIX 1: Use DocumentId on a dedicated, unique property name
    // to avoid collision with the 'id' field present in the document data.
    // However, since the error states 'id' was found, we will use 'documentId'
    // and rely on the database field 'uid' for the actual user ID if needed,
    // or simply rely on the documentId.
    @DocumentId
    val documentId: String = "",

    // Keeping 'uid' for compatibility, though it is usually redundant if documentId is used.
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val displayName: String? = null,

    // ⭐ FIX 3: Renamed photoURL/phoneNumber fields to match common Firestore convention
    @PropertyName("profileImage") // Assuming your database uses 'profileImage'
    val profileImage: String? = null,

    @PropertyName("phone") // ⭐ CRITICAL FIX: Match the database field name 'phone'
    val phone: String? = null,

    val gameId: String? = null,
    val gameMode: String? = null,
    val country: String = "",
    val countryCode: String? = null,
    val currency: String = "INR",
    val walletBalance: Double = 0.0,
    val kycStatus: String = "not_submitted",
    val role: String = "user",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
    // Optional/admin/Firestore-only fields
    val lastLogin: Timestamp? = null,

    @PropertyName("isEmailVerified") // Assuming your database uses 'isEmailVerified'
    val isVerified: Boolean = false,

    val kycDocuments: KycDocuments? = null,
    val status: String = "active",
    val location: String? = null
) {
    companion object {
        fun fromFirestore(
            id: String, // Kept to receive the document ID if mapping manually
            uid: String,
            email: String,
            username: String,
            displayName: String? = null,
            photoURL: String? = null,
            phoneNumber: String? = null,
            gameId: String? = null,
            gameMode: String? = null,
            country: String = "",
            countryCode: String? = null,
            currency: String = "INR",
            walletBalance: Double = 0.0,
            kycStatus: String = "not_submitted",
            role: String = "user",
            createdAt: Timestamp,
            updatedAt: Timestamp? = null,
            lastLogin: Timestamp? = null,
            isVerified: Boolean = false,
            kycDocuments: Map<String, String>? = null,
            status: String = "active",
            location: String? = null
        ): User {
            return User(
                documentId = id, // Map document ID to the new field
                uid = uid,
                email = email,
                username = username,
                displayName = displayName,
                profileImage = photoURL, // Mapping from old param to new field
                phone = phoneNumber, // Mapping from old param to new field
                gameId = gameId,
                gameMode = gameMode,
                country = country,
                countryCode = countryCode,
                currency = currency,
                walletBalance = walletBalance,
                kycStatus = kycStatus,
                role = role,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastLogin = lastLogin,
                isVerified = isVerified,
                kycDocuments = kycDocuments?.let { KycDocuments.fromMap(it) },
                status = status,
                location = location
            )
        }
    }

    fun toFirestore(): Map<String, Any> {
        return mapOf(
            "uid" to uid,
            "email" to email,
            "username" to username,
            "displayName" to displayName,
            "profileImage" to profileImage, // Changed from photoURL
            "phone" to phone,             // Changed from phoneNumber
            "gameId" to gameId,
            "gameMode" to gameMode,
            "country" to country,
            "countryCode" to countryCode,
            "currency" to currency,
            "walletBalance" to walletBalance,
            "kycStatus" to kycStatus,
            "role" to role,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "lastLogin" to lastLogin,
            "isEmailVerified" to isVerified, // Match DB field name (if different from isVerified)
            "kycDocuments" to kycDocuments?.toMap(),
            "status" to status,
            "location" to location
        ).filterValues { it != null } as Map<String, Any>
    }
}

data class KycDocuments(
    val idProof: String? = null,
    val addressProof: String? = null,
    val selfie: String? = null
) {
    fun toMap(): Map<String, String?> {
        return mapOf(
            "idProof" to idProof,
            "addressProof" to addressProof,
            "selfie" to selfie
        )
    }

    companion object {
        fun fromMap(map: Map<String, String>): KycDocuments {
            return KycDocuments(
                idProof = map["idProof"],
                addressProof = map["addressProof"],
                selfie = map["selfie"]
            )
        }
    }
}