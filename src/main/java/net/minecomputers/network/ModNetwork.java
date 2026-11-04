package net.minecomputers.network;

import net.minecomputers.client.ClientScreens;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers the mod's network packets. */
public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(LaptopSnapshot.TYPE, LaptopSnapshot.STREAM_CODEC,
                (payload, context) -> ClientScreens.onSnapshot(payload));
        registrar.playToClient(ConsoleSync.TYPE, ConsoleSync.STREAM_CODEC,
                (payload, context) -> ClientScreens.onConsole(payload));
        registrar.playToClient(LaptopNotice.TYPE, LaptopNotice.STREAM_CODEC,
                (payload, context) -> ClientScreens.onNotice(payload));
        registrar.playToClient(ScreenUpdate.TYPE, ScreenUpdate.STREAM_CODEC,
                (payload, context) -> ClientScreens.onScreenUpdate(payload));
        registrar.playToClient(MediaPlay.TYPE, MediaPlay.STREAM_CODEC,
                (payload, context) -> ClientScreens.onMediaPlay(payload));
        registrar.playToClient(MediaStop.TYPE, MediaStop.STREAM_CODEC,
                (payload, context) -> ClientScreens.onMediaStop(payload));
        registrar.playToClient(MediaData.TYPE, MediaData.STREAM_CODEC,
                (payload, context) -> ClientScreens.onMediaData(payload));
        registrar.playToClient(VideoPlay.TYPE, VideoPlay.STREAM_CODEC,
                (payload, context) -> ClientScreens.onVideoPlay(payload));
        registrar.playToClient(VideoStop.TYPE, VideoStop.STREAM_CODEC,
                (payload, context) -> ClientScreens.onVideoStop(payload));
        registrar.playToClient(HackState.TYPE, HackState.STREAM_CODEC,
                (payload, context) -> ClientScreens.onHackState(payload));
        registrar.playToServer(HackAction.TYPE, HackAction.STREAM_CODEC, HackManager::handle);
        registrar.playToServer(MediaRequest.TYPE, MediaRequest.STREAM_CODEC, MediaTransfer::handleRequest);
        registrar.playToServer(LaptopAction.TYPE, LaptopAction.STREAM_CODEC, ServerHandlers::handleAction);
        registrar.playToServer(LaptopUpload.TYPE, LaptopUpload.STREAM_CODEC, ServerHandlers::handleUpload);
    }

    private ModNetwork() {
    }
}
