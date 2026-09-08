package com.entitygenerator.db

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "EntityGeneratorConnections", storages = [Storage("entity-generator-connections.xml")])
@Service(Service.Level.APP)
class ConnectionStorageService : PersistentStateComponent<ConnectionStorageService.State> {

    class State {
        var connections: MutableList<SavedConnection> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    fun list(): List<SavedConnection> = myState.connections.toList()

    fun save(connection: SavedConnection) {
        myState.connections.removeIf { it.id == connection.id }
        myState.connections.add(connection)
    }

    fun delete(id: String) {
        myState.connections.removeIf { it.id == id }
    }

    companion object {
        fun getInstance(): ConnectionStorageService =
            ApplicationManager.getApplication().getService(ConnectionStorageService::class.java)
    }
}