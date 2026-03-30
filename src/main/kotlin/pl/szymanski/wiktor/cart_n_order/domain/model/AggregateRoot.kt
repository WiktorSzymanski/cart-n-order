package pl.szymanski.wiktor.cart_n_order.domain.model

abstract class AggregateRoot<E : DomainEvent> {

    var version: Long = 0L
        private set

    private val _uncommittedEvents: MutableList<E> = mutableListOf()
    val uncommittedEvents: List<E> get() = _uncommittedEvents.toList()

    fun markCommitted() = _uncommittedEvents.clear()

    /** Apply event, increment version, and queue as uncommitted. Called for new state changes. */
    protected fun raise(event: E) {
        applyEvent(event)
        version++
        _uncommittedEvents.add(event)
    }

    /** Apply event and increment version without queuing. Called during reconstitution from history. */
    protected fun replay(event: E) {
        applyEvent(event)
        version++
    }

    /** Restore version from a snapshot without applying any events. */
    protected fun restoreVersion(v: Long) {
        version = v
    }

    abstract fun applyEvent(event: E)
}
