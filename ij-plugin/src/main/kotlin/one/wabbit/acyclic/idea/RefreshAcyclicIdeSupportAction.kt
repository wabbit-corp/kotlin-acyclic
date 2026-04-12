// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.acyclic.idea

import one.wabbit.ijplugin.common.ConfiguredRefreshIdeSupportAction

class RefreshAcyclicIdeSupportAction : ConfiguredRefreshIdeSupportAction(
    "Refresh Acyclic IDE Support",
    "Re-scan Kotlin compiler arguments and enable acyclic IDE support for this project session",
    AcyclicIdeSupportCoordinator,
) {
}
