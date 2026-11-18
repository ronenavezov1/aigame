package com.aigame.state

import com.aigame.objects.HostEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppStateManager {

    private val _hostMemoryState = MutableStateFlow(HostMemoryState())
    val hostMemoryState: StateFlow<HostMemoryState> = _hostMemoryState.asStateFlow()

    fun updateHostMemory(updater: (HostMemoryState) -> HostMemoryState) {
        _hostMemoryState.update(updater)
    }

    private val _hostState = MutableStateFlow(HostState())
    val hostState: StateFlow<HostState> = _hostState.asStateFlow()

    fun updateHost(updater: (HostState) -> HostState) {
        _hostState.update(updater)
    }

    private val _microphoneState = MutableStateFlow(MicrophoneState())
    val microphoneState: StateFlow<MicrophoneState> = _microphoneState.asStateFlow()

    fun updateMicrophone(updater: (MicrophoneState) -> MicrophoneState) {
        _microphoneState.update(updater)
    }

    private val _voiceprintState = MutableStateFlow(VoiceprintState())
    val voiceprintState: StateFlow<VoiceprintState> = _voiceprintState.asStateFlow()

    fun updateVoiceprint(updater: (VoiceprintState) -> VoiceprintState) {
        _voiceprintState.update(updater)
    }

    private val _environmentState = MutableStateFlow(EnvironmentState())
    val environmentState: StateFlow<EnvironmentState> = _environmentState.asStateFlow()

    fun updateEnvironment(updater: (EnvironmentState) -> EnvironmentState) {
        _environmentState.update(updater)
    }

    // מה שמעיר את המארח — משפט שנשמע, או התעוררות. כל אירוע הוא תור אחד של הסוכן.
    private val _hostEventFlow = MutableSharedFlow<HostEvent>(
        replay = 0, // אירוע שעבר לא מעניין מי שרק עכשיו התחיל להאזין
        extraBufferCapacity = 50, // אורחים מדברים גם כשהמארח עוד עונה — האירועים מחכים בתור
        onBufferOverflow = BufferOverflow.DROP_OLDEST, // אם בכל זאת התמלא, הישן ביותר נזרק
    )
    val hostEventFlow: SharedFlow<HostEvent> = _hostEventFlow.asSharedFlow()

    fun addHostEvent(event: HostEvent) {
        _hostEventFlow.tryEmit(event)
    }
}
