package net.sorenon.titleworlds.mixin.accessor;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPacksSource.class)
public interface ServerPacksSourceAcc {
    @Invoker
    static PackRepository invokeCreatePackRepository(LevelStorageSource.LevelStorageAccess levelStorageAccess) {
        throw new UnsupportedOperationException();
    }


}
