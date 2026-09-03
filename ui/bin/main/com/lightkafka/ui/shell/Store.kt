package com.lightkafka.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Generic MVI Store backed by MutableStateFlow + a pure reducer function.
 *
 * @param State  immutable app-state type
 * @param Action sealed interface of intents the store accepts
 */
class Store<State, Action>(
    initialState: State,
    private val reducer: (State, Action) -> State,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    val value: State get() = _state.value

    /** Dispatch an action through the reducer and emit the resulting state. */
    fun dispatch(action: Action) {
        _state.value = reducer(_state.value, action)
    }
}
