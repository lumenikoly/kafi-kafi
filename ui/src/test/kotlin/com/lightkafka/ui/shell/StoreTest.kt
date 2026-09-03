package com.lightkafka.ui.shell

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Smoke test for the generic [Store] class using a simple counter reducer.
 * Verifies the MutableStateFlow → dispatch → collect cycle works correctly.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StoreTest {
    // Simple counter: state is an Int, action is an Int delta
    private val counterReducer: (Int, Int) -> Int = { state, delta -> state + delta }

    @Test
    fun `initial state is exposed via value`() {
        val store = Store(initialState = 0, reducer = counterReducer)
        assertEquals(0, store.value)
    }

    @Test
    fun `dispatch applies reducer and updates value`() {
        val store = Store(initialState = 0, reducer = counterReducer)
        store.dispatch(5)
        assertEquals(5, store.value)
    }

    @Test
    fun `multiple dispatches accumulate state`() {
        val store = Store(initialState = 0, reducer = counterReducer)
        store.dispatch(3)
        store.dispatch(7)
        store.dispatch(-2)
        assertEquals(8, store.value)
    }

    @Test
    fun `state flow emits latest value`() =
        runTest {
            val store = Store(initialState = 10, reducer = counterReducer)
            store.dispatch(5)
            assertEquals(15, store.state.first())
        }

    @Test
    fun `state flow emits on each dispatch`() {
        val store = Store(initialState = 0, reducer = counterReducer)
        val values = mutableListOf<Int>()
        // MutableStateFlow dispatches synchronously, so we can collect in a simple loop
        values.add(store.state.value)
        store.dispatch(1)
        values.add(store.state.value)
        store.dispatch(2)
        values.add(store.state.value)
        store.dispatch(3)
        values.add(store.state.value)
        assertEquals(listOf(0, 1, 3, 6), values)
    }

    @Test
    fun `toggle-style reducer works correctly`() {
        data class ToggleState(
            val on: Boolean,
        )
        val toggleReducer: (ToggleState, Unit) -> ToggleState = { state, _ ->
            state.copy(on = !state.on)
        }
        val store = Store(initialState = ToggleState(on = false), reducer = toggleReducer)
        assertEquals(false, store.value.on)
        store.dispatch(Unit)
        assertEquals(true, store.value.on)
        store.dispatch(Unit)
        assertEquals(false, store.value.on)
    }
}
