package com.sayists.shyware

import org.junit.Assert.assertThrows
import org.junit.Test

class UnsignedManifestTest {
    private fun config(contract: String, flows: List<String>) = ShyConfig(
        contractVersion = contract,
        app = AppConfig("unsigned-test"),
        api = ApiConfig("https://example.test"),
        identity = IdentityConfig("didit", "attested"),
        signing = SigningConfig(false, "none"),
        anonLayer = AnonLayerConfig(true, flows),
        deployment = DeploymentConfig("coercion_resistant", RuntimeFallbacks()),
        wire = WireConfig(), custody = CustodyConfig(), contracts = ContractsConfig(),
        governance = GovernanceConfig(), execution = ExecutionConfig(),
    )

    @Test fun unsignedDomainsStillEnforceStructuralConfiguration() {
        val cases: List<Pair<ShyConfig, (ShyConfig) -> Unit>> = listOf(
            config("shyvoting-v1", listOf("poll_read", "ballot_build", "ballot_submit", "receipt_verify")) to ::assertVotingManifest,
            config("shywire-v1", listOf("wire_issue", "wire_transfer", "wire_redeem")) to ::assertWireManifest,
            config("shycontracts-v1", listOf("contract_register", "contract_activate", "contract_execute")) to ::assertContractsManifest,
            config("shycustody-v1", listOf("policy_read", "lot_record", "silo_transfer", "redemption_request", "redemption_settlement", "demurrage_apply")) to ::assertCustodyManifest,
            config("shyshares-v1", listOf("organization_read", "membership_snapshot_read", "proposal_create", "weighted_ballot_submit", "tally_read", "action_queue_read", "action_dispatch")) to ::assertSharesManifest,
            config("shybets-v1", emptyList()) to ::assertBetsManifest,
            config("shylots-v1", emptyList()) to ::assertLotsManifest,
        )
        for ((manifest, validate) in cases) {
            validate(manifest)
            validate(manifest.copy(signing = SigningConfig(false, "software")))
            assertThrows(IllegalArgumentException::class.java) {
                validate(manifest.copy(anonLayer = manifest.anonLayer.copy(blackBoxRequired = false)))
            }
        }
        val voting = cases.first().first
        assertThrows(IllegalArgumentException::class.java) { assertVotingManifest(voting.copy(identity = IdentityConfig("none", "none"))) }
        assertThrows(IllegalArgumentException::class.java) { assertVotingManifest(voting.copy(anonLayer = AnonLayerConfig(true, emptyList()))) }
    }
}
