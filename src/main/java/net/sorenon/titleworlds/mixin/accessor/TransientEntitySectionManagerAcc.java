package net.sorenon.titleworlds.mixin.accessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TransientEntitySectionManager.class)
public interface TransientEntitySectionManagerAcc {

    @Accessor
    EntitySectionStorage<Entity> getSectionStorage();

}
