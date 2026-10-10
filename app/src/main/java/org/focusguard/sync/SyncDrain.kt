package org.focusguard.sync
import org.focusguard.data.Outbox
object SyncDrain {
    suspend fun run(operations: List<Outbox>,canContinue: suspend ()->Boolean,upload: suspend (Outbox)->Unit,acknowledge: suspend (String)->Unit) {
        for(op in operations) {
            if(!canContinue()) return
            upload(op)
            acknowledge(op.id)
        }
    }
}
