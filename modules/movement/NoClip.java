package moscow.rockstar.systems.modules.modules.movement;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.network.ReceivePacketEvent;
import moscow.rockstar.systems.event.impl.network.SendPacketEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

@ModuleInfo(name = "No Clip", category = ModuleCategory.MOVEMENT)
public class NoClip extends BaseModule {
   private final List<Packet<?>> delayedPackets = new CopyOnWriteArrayList<>();
   private int ticksSinceFlush;
   private boolean flushing;
   private final EventListener<SendPacketEvent> onSendPacket = event -> {
      Packet<?> packet = event.getPacket();
      if (!this.flushing && this.isInsideSolidBlock() && !(packet instanceof KeepAliveC2SPacket) && !(packet instanceof CommonPongC2SPacket)) {
         this.delayedPackets.add(packet);
         event.cancel();
      }
   };
   private final EventListener<ReceivePacketEvent> onReceivePacket = event -> {
      if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
         this.flushPackets();
         this.sendFullMovePacket();
      }
   };

   @Override
   public void tick() {
      if (mc.player != null && mc.world != null) {
         if (++this.ticksSinceFlush >= 10) {
            this.flushPackets();
            this.ticksSinceFlush = 0;
         }

         if (this.isInsideSolidBlock()) {
            this.sendFullMovePacket();
         }

         mc.player.setVelocity(mc.player.getVelocity().x, 0.0, mc.player.getVelocity().z);
         super.tick();
      } else {
         this.disable();
      }
   }

   @Override
   public void onDisable() {
      this.flushPackets();
      this.ticksSinceFlush = 0;
      super.onDisable();
   }

   private boolean isInsideSolidBlock() {
      if (mc.player != null && mc.world != null) {
         Box playerBox = mc.player.getBoundingBox();
         BlockPos min = BlockPos.ofFloored(playerBox.minX, playerBox.minY, playerBox.minZ);
         BlockPos max = BlockPos.ofFloored(playerBox.maxX, playerBox.maxY, playerBox.maxZ);

         for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
               for (int z = min.getZ(); z <= max.getZ(); z++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  BlockState state = mc.world.getBlockState(pos);
                  if (!state.isAir()) {
                     VoxelShape shape = state.getCollisionShape(mc.world, pos);
                     Box movedBox = playerBox.offset(-pos.getX(), -pos.getY(), -pos.getZ());
                     if (shape.getBoundingBoxes().stream().anyMatch(box -> box.intersects(movedBox))) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void sendFullMovePacket() {
      if (mc.player != null && mc.getNetworkHandler() != null) {
         this.flushing = true;

         try {
            mc.getNetworkHandler()
               .sendPacket(
                  new Full(
                     mc.player.getX(),
                     mc.player.getY(),
                     mc.player.getZ(),
                     mc.player.getYaw(),
                     mc.player.getPitch(),
                     mc.player.isOnGround(),
                     false
                  )
               );
         } finally {
            this.flushing = false;
         }
      }
   }

   private void flushPackets() {
      if (mc.getNetworkHandler() != null && !this.delayedPackets.isEmpty()) {
         this.flushing = true;

         try {
            for (Packet<?> packet : this.delayedPackets) {
               mc.getNetworkHandler().sendPacket(packet);
            }
         } finally {
            this.delayedPackets.clear();
            this.flushing = false;
         }
      }
   }
}
