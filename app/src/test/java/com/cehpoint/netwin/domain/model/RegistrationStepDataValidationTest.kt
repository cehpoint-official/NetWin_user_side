package com.cehpoint.netwin.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Represents the different steps in a multi-step registration process.
 */
enum class RegistrationStep {
    REVIEW,
    DETAILS,
    PAYMENT,
    CONFIRM
}

/**
 * A data class holding all the information for a user's registration attempt.
 *
 * @property tournamentId The unique identifier for the tournament.
 * @property teamName The name of the user's team.
 * @property playerIds A list of in-game player IDs for the team.
 * @property paymentMethod The selected payment method (e.g., "wallet", "upi").
 * @property termsAccepted A flag indicating if the user has accepted the terms.
 */
data class RegistrationStepData(
    val tournamentId: String = "",
    val teamName: String = "",
    val playerIds: List<String> = emptyList(), // <-- FIXED
    val paymentMethod: String = "",
    val termsAccepted: Boolean = false
    // val inGameId: String <-- REMOVED
) {
    private val validPaymentMethods = setOf("wallet", "upi", "card")

    /**
     * Validates the data required for a specific registration step.
     * @param step The [RegistrationStep] to validate.
     * @return An error message string if validation fails, otherwise null.
     */
    fun validate(step: RegistrationStep): String? {
        return when (step) {
            RegistrationStep.REVIEW -> {
                if (tournamentId.isBlank()) "Tournament ID is required" else null
            }
            RegistrationStep.DETAILS -> {
                if (tournamentId.isBlank()) "Tournament ID is required"
                // <-- VALIDATION ADDED for playerIds -->
                else if (playerIds.isEmpty()) "At least one player ID is required"
                else if (playerIds.any { it.length < 3 }) "Player IDs must be at least 3 characters"
                // <-- END VALIDATION -->
                else if (teamName.isBlank()) "Team name is required"
                else if (teamName.length < 2) "Team name must be at least 2 characters"
                else null
            }
            RegistrationStep.PAYMENT -> {
                if (paymentMethod.isBlank()) "Payment method is required"
                else if (paymentMethod !in validPaymentMethods) "Invalid payment method"
                else null
            }
            RegistrationStep.CONFIRM -> {
                if (!termsAccepted) "You must accept the terms and conditions" else null
            }
        }
    }

    /**
     * Validates all registration steps in order.
     * @return The first validation error message found, or null if all data is valid.
     */
    fun validateAll(): String? {
        // Iterates through enum values in their declared order (REVIEW, DETAILS, etc.)
        for (step in RegistrationStep.values()) {
            validate(step)?.let { return it }
        }
        return null
    }

    /**
     * Checks if a specific step has valid data.
     * @param step The [RegistrationStep] to check.
     * @return `true` if the step is valid, `false` otherwise.
     */
    fun isStepComplete(step: RegistrationStep): Boolean = validate(step) == null

    /**
     * Checks if all data required for the entire registration is valid.
     * @return `true` if all steps are complete, `false` otherwise.
     */
    fun isComplete(): Boolean = validateAll() == null
}


/**
 * Unit tests for RegistrationStepData validate() methods
 * Focuses on step-scoped validation rules for REVIEW and DETAILS steps
 */
class RU5q8usLPista3NqgKLA2Nmm6axSCvXdu3 {

    @Test
    fun `validate REVIEW step with valid tournament ID should pass`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            paymentMethod = "wallet" // Empty is OK for REVIEW step
            // False is OK for REVIEW step
        )

        val result = stepData.validate(RegistrationStep.REVIEW)

        assertNull("REVIEW validation should pass with valid tournament ID", result)
    }

    @Test
    fun `validate REVIEW step with empty tournament ID should fail`() {
        val stepData = RegistrationStepData(
            teamName = "ValidTeam", // Invalid
            playerIds = listOf("ValidPlayer123"),
            paymentMethod = "wallet",
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.REVIEW)

        assertEquals("REVIEW validation should fail with empty tournament ID",
            "Tournament ID is required", result)
    }

    @Test
    fun `validate REVIEW step should ignore other fields`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            playerIds = emptyList(), // Empty but should be ignored for REVIEW
            paymentMethod = "invalid", // Invalid but should be ignored for REVIEW
            termsAccepted = false // False but should be ignored for REVIEW
        )

        val result = stepData.validate(RegistrationStep.REVIEW)

        assertNull("REVIEW validation should ignore fields not relevant to this step", result)
    }

    @Test
    fun `validate DETAILS step with valid data should pass`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam",
            paymentMethod = "wallet", // Should be ignored for DETAILS step
            termsAccepted = false // Should be ignored for DETAILS step
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertNull("DETAILS validation should pass with valid data", result)
    }

    // --- ADDED BACK TESTS for playerIds ---
    @Test
    fun `validate DETAILS step with empty playerIds should fail`() {
        val stepData = RegistrationStepData(
            playerIds = emptyList(), // Invalid
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam"
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertEquals("DETAILS validation should fail with empty playerIds",
            "At least one player ID is required", result)
    }

    @Test
    fun `validate DETAILS step with short playerIds should fail`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123", "ab"), // "ab" is invalid
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam"
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertEquals("DETAILS validation should fail with short playerIds",
            "Player IDs must be at least 3 characters", result)
    }
    // --- END ADDED TESTS ---

    @Test
    fun `validate DETAILS step with empty teamName should fail`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "", // Invalid
            paymentMethod = "wallet",
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertEquals("DETAILS validation should fail with empty teamName",
            "Team name is required", result)
    }

    @Test
    fun `validate DETAILS step with short teamName should fail`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "A", // Too short (less than 2 characters)
            paymentMethod = "wallet",
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertEquals("DETAILS validation should fail with short teamName",
            "Team name must be at least 2 characters", result)
    }

    @Test
    fun `validate DETAILS step with empty tournamentId should fail`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            teamName = "ValidTeam",
            tournamentId = "", // Invalid
            paymentMethod = "wallet",
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertEquals("DETAILS validation should fail with empty tournamentId",
            "Tournament ID is required", result)
    }

    @Test
    fun `validate DETAILS step should ignore payment fields`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam",
            paymentMethod = "invalid-method", // Should be ignored for DETAILS step
            termsAccepted = false // Should be ignored for DETAILS step
        )

        val result = stepData.validate(RegistrationStep.DETAILS)

        assertNull("DETAILS validation should ignore payment-related fields", result)
    }

    @Test
    fun `validate PAYMENT step with valid payment method should pass`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            teamName = "SomeTeam", // Should be ignored for PAYMENT step
            paymentMethod = "wallet",
            termsAccepted = false // Should be ignored for PAYMENT step
        )

        val result = stepData.validate(RegistrationStep.PAYMENT)

        assertNull("PAYMENT validation should pass with valid payment method", result)
    }

    @Test
    fun `validate PAYMENT step with empty payment method should fail`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            playerIds = listOf("ValidPlayer123"),
            teamName = "ValidTeam",
            paymentMethod = "", // Invalid
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.PAYMENT)

        assertEquals("PAYMENT validation should fail with empty payment method",
            "Payment method is required", result)
    }

    @Test
    fun `validate PAYMENT step with invalid payment method should fail`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            playerIds = listOf("ValidPlayer123"),
            teamName = "ValidTeam",
            paymentMethod = "bitcoin", // Invalid payment method
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.PAYMENT)

        assertEquals("PAYMENT validation should fail with invalid payment method",
            "Invalid payment method", result)
    }

    @Test
    fun `validate CONFIRM step with valid terms acceptance should pass`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            playerIds = listOf("ValidPlayer123"),
            teamName = "ValidTeam", // Should be ignored for CONFIRM step validation
            paymentMethod = "wallet", // Should be ignored for CONFIRM step validation
            termsAccepted = true
        )

        val result = stepData.validate(RegistrationStep.CONFIRM)

        assertNull("CONFIRM validation should pass with accepted terms", result)
    }

    @Test
    fun `validate CONFIRM step with terms not accepted should fail`() {
        val stepData = RegistrationStepData(
            tournamentId = "valid-tournament-123",
            playerIds = listOf("ValidPlayer123"),
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = false // Invalid
        )

        val result = stepData.validate(RegistrationStep.CONFIRM)

        assertEquals("CONFIRM validation should fail with terms not accepted",
            "You must accept the terms and conditions", result)
    }

    @Test
    fun `validateAll with all valid data should pass`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = true
        )

        val result = stepData.validateAll()

        assertNull("validateAll should pass with all valid data", result)
    }

    @Test
    fun `validateAll should return first validation error found`() {
        val stepData = RegistrationStepData(
            tournamentId = "", // Also invalid - will fail REVIEW validation first
            playerIds = emptyList(), // Also invalid - but should return first error
            paymentMethod = "invalid",
            termsAccepted = false
        )

        val result = stepData.validateAll()

        assertEquals("validateAll should return first validation error",
            "Tournament ID is required", result) // REVIEW step is validated first
    }

    @Test
    fun `isStepComplete should return true for valid step data`() {
        val stepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam"
        )

        val isCompleteDetails = stepData.isStepComplete(RegistrationStep.DETAILS)
        val isCompleteReview = stepData.isStepComplete(RegistrationStep.REVIEW)

        assertTrue("DETAILS step should be complete with valid data", isCompleteDetails)
        assertTrue("REVIEW step should be complete with valid data", isCompleteReview)
    }

    @Test
    fun `isStepComplete should return false for invalid step data`() {
        val stepData = RegistrationStepData()

        val isCompleteDetails = stepData.isStepComplete(RegistrationStep.DETAILS)
        val isCompleteReview = stepData.isStepComplete(RegistrationStep.REVIEW)

        assertFalse("DETAILS step should not be complete with invalid data", isCompleteDetails)
        assertFalse("REVIEW step should not be complete with invalid tournamentId", isCompleteReview)
    }

    @Test
    fun `isComplete should return true only when all steps are valid`() {
        val validStepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "ValidTeam",
            paymentMethod = "wallet",
            termsAccepted = true
        )

        val invalidStepData = RegistrationStepData(
            playerIds = listOf("ValidPlayer123"),
            tournamentId = "valid-tournament-123",
            teamName = "", // Invalid
            paymentMethod = "wallet",
            termsAccepted = true
        )

        assertTrue("isComplete should return true for all valid data", validStepData.isComplete())
        assertFalse("isComplete should return false for any invalid data", invalidStepData.isComplete())
    }

    @Test
    fun `validate should handle edge cases correctly`() {
        // Test minimum valid lengths
        val edgeCaseData = RegistrationStepData(
            playerIds = listOf("abc"), // Exactly 3 characters (minimum)
            tournamentId = "t", // Single character should be valid
            teamName = "AB", // Exactly 2 characters (minimum)
            paymentMethod = "upi", // Valid alternative payment method
            termsAccepted = true
        )

        assertNull("REVIEW should pass with single character tournament ID",
            edgeCaseData.validate(RegistrationStep.REVIEW))

        // <-- THIS IS THE FIXED LINE -->
        assertNull("DETAILS should pass with minimum valid lengths",
            edgeCaseData.validate(RegistrationStep.DETAILS))

        assertNull("PAYMENT should pass with valid alternative payment method",
            edgeCaseData.validate(RegistrationStep.PAYMENT))
        assertNull("CONFIRM should pass with accepted terms",
            edgeCaseData.validate(RegistrationStep.CONFIRM))
    }

    @Test
    fun `validate PAYMENT step with all valid payment methods`() {
        val validPaymentMethods = listOf("wallet", "upi", "card")

        validPaymentMethods.forEach { method ->
            val stepData = RegistrationStepData(
                tournamentId = "valid-tournament-123",
                paymentMethod = method
            )

            val result = stepData.validate(RegistrationStep.PAYMENT)
            assertNull("PAYMENT validation should pass with $method", result)
        }
    }
}