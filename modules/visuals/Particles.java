package moscow.rockstar.systems.modules.modules.visuals;

import com.mojang.blaze3d.platform.GlStateManager.DstFactor;
import com.mojang.blaze3d.platform.GlStateManager.SrcFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import moscow.rockstar.Rockstar;
import moscow.rockstar.systems.event.EventListener;
import moscow.rockstar.systems.event.impl.game.AttackEvent;
import moscow.rockstar.systems.event.impl.player.ClientPlayerTickEvent;
import moscow.rockstar.systems.event.impl.render.Render3DEvent;
import moscow.rockstar.systems.modules.api.ModuleCategory;
import moscow.rockstar.systems.modules.api.ModuleInfo;
import moscow.rockstar.systems.modules.impl.BaseModule;
import moscow.rockstar.systems.setting.settings.BooleanSetting;
import moscow.rockstar.systems.setting.settings.ColorSetting;
import moscow.rockstar.systems.setting.settings.SelectSetting;
import moscow.rockstar.systems.setting.settings.SliderSetting;
import moscow.rockstar.utility.animation.base.Animation;
import moscow.rockstar.utility.animation.base.Easing;
import moscow.rockstar.utility.colors.ColorRGBA;
import moscow.rockstar.utility.colors.Colors;
import moscow.rockstar.utility.math.MathUtility;
import moscow.rockstar.utility.time.Timer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@ModuleInfo(name = "Particles", category = ModuleCategory.VISUALS, desc = "modules.descriptions.particles")
@Environment(EnvType.CLIENT)
public class Particles extends BaseModule {
    private final BooleanSetting syncWithTheme = new BooleanSetting(this, "modules.sync.theme").enable();
    private final ColorSetting color = new ColorSetting(this, "modules.particles.settings.color", () -> this.syncWithTheme.isEnabled()).color(Colors.ACCENT);
    private final SelectSetting textures = new SelectSetting(this, "modules.particles.settings.textures");
    private final SelectSetting.Value texStar = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_star");
    private final SelectSetting.Value texHeart = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_heart").select();
    private final SelectSetting.Value texCross = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_cross");
    private final SelectSetting.Value texLightning = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_lightning");
    private final SelectSetting.Value texFirefly = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_firefly");
    private final SelectSetting.Value texSnow = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_snow");
    private final SelectSetting.Value texPumpkin = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_pumpkin");
    private final SelectSetting.Value texDollar = new SelectSetting.Value(this.textures, "modules.particles.settings.tex_dollar");
    private final SelectSetting reason = new SelectSetting(this, "modules.particles.settings.reason");
    private final SelectSetting.Value idle = new SelectSetting.Value(this.reason, "modules.particles.settings.reason_idle").select();
    private final SelectSetting.Value run = new SelectSetting.Value(this.reason, "modules.particles.settings.reason_run");
    private final SelectSetting.Value hit = new SelectSetting.Value(this.reason, "modules.particles.settings.reason_hit").select();
    private final SelectSetting.Value pearlReason = new SelectSetting.Value(this.reason, "modules.particles.settings.reason_pearl").select();
    private final SelectSetting.Value tridentReason = new SelectSetting.Value(this.reason, "modules.particles.settings.reason_trident").select();
    private final SelectSetting.Value arrowReason = new SelectSetting.Value(this.reason, "modules.particles.settings.reason_arrow").select();
    private final SliderSetting particleSize = new SliderSetting(this, "modules.particles.settings.size").min(0.1F).max(1.0F).step(0.05F).currentValue(0.4F);
    private final SliderSetting count = new SliderSetting(this, "modules.particles.settings.count").min(1.0F).max(40.0F).step(1.0F).currentValue(7.0F);
    private final SliderSetting hitCount = new SliderSetting(this, "modules.particles.settings.hit_count").min(1.0F).max(50.0F).step(1.0F).currentValue(7.0F);
    private final List<Particles.Particle> particles = new CopyOnWriteArrayList<>();
    private final List<Identifier> activeTextures = new ArrayList<>();
    private final EventListener<Render3DEvent> onRender3D = event -> {
        if (mc.world != null) {
            this.particles.removeIf(px -> px.alpha.getValue() == 0.0F && px.timer.finished(px.lifeTime));
            if (!this.particles.isEmpty()) {
                this.refreshActiveTextures();
                if (!this.activeTextures.isEmpty()) {
                    MatrixStack ms = event.getMatrices();
                    Vec3d camPos = mc.gameRenderer.getCamera().getPos();
                    Quaternionf camRot = mc.gameRenderer.getCamera().getRotation();
                    Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F);
                    Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
                    right.rotate(camRot);
                    up.rotate(camRot);
                    ColorRGBA c = this.syncWithTheme.isEnabled() ? Colors.getAccent() : this.color.getColor();
                    int globalR = (int)c.getRed();
                    int globalG = (int)c.getGreen();
                    int globalB = (int)c.getBlue();
                    ms.push();
                    RenderSystem.enableBlend();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                    RenderSystem.blendFunc(SrcFactor.SRC_ALPHA, DstFactor.ONE);
                    RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                    Matrix4f mat = ms.peek().getPositionMatrix();
                    float size = this.particleSize.getCurrentValue();
                    RenderSystem.setShaderTexture(0, Rockstar.id("textures/bloom.png"));
                    BufferBuilder buf = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                    for (Particles.Particle p : this.particles) {
                        float a = p.alpha.getValue();
                        if (!(a <= 0.0F)) {
                            Vec3d pos = lerp3(p.prevPos, p.pos, event.getTickDelta());
                            int cr = p.hasCustomColor ? p.customR : globalR;
                            int cg = p.hasCustomColor ? p.customG : globalG;
                            int cb = p.hasCustomColor ? p.customB : globalB;
                            writeQuad(buf, mat, pos, camPos, right, up, size * 1.4F / 2.0F, cr, cg, cb, clamp((int)(a * 255.0F * 0.28F)));
                        }
                    }

                    BuiltBuffer built = buf.endNullable();
                    if (built != null) {
                        BufferRenderer.drawWithGlobalProgram(built);
                    }

                    for (Identifier tex : this.activeTextures) {
                        BufferBuilder bufx = RenderSystem.renderThreadTesselator().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                        for (Particles.Particle p : this.particles) {
                            if (p.texture.equals(tex)) {
                                float a = p.alpha.getValue();
                                if (!(a <= 0.0F)) {
                                    Vec3d pos = lerp3(p.prevPos, p.pos, event.getTickDelta());
                                    int cr = p.hasCustomColor ? p.customR : globalR;
                                    int cg = p.hasCustomColor ? p.customG : globalG;
                                    int cb = p.hasCustomColor ? p.customB : globalB;
                                    writeQuad(bufx, mat, pos, camPos, right, up, size / 2.0F, cr, cg, cb, clamp((int)(a * 255.0F * 0.85F)));
                                }
                            }
                        }

                        BuiltBuffer builtx = bufx.endNullable();
                        if (builtx != null) {
                            RenderSystem.setShaderTexture(0, tex);
                            BufferRenderer.drawWithGlobalProgram(builtx);
                        }
                    }

                    RenderSystem.depthMask(true);
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.enableCull();
                    RenderSystem.disableBlend();
                    ms.pop();
                }
            }
        }
    };
    private final EventListener<ClientPlayerTickEvent> onPlayerTick = event -> {
        if (mc.player != null && mc.world != null) {
            for (Particles.Particle p : this.particles) {
                p.tick();
            }

            if (this.run.isSelected() && this.isMoving()) {
                Vec3d motion = mc.player.getVelocity();
                double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
                boolean elytra = !mc.player.isOnGround()
                        && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                        && motion.lengthSquared() > 0.1;
                Vec3d dir;
                if (speed < 0.01) {
                    dir = mc.player.getRotationVec(1.0F).multiply(-1.0);
                } else if (elytra) {
                    dir = motion.normalize().multiply(-1.0);
                } else {
                    dir = new Vec3d(-motion.x / speed, 0.0, -motion.z / speed);
                }

                double dist = (elytra ? 1.2 : 0.5) + (speed > 0.1 ? speed * 1.5 : 0.0);
                double px = mc.player.getX() + dir.x * dist + MathUtility.random(-0.35F, 0.35F);
                double py = elytra
                        ? mc.player.getY() + mc.player.getHeight() / 2.0 + dir.y * dist + MathUtility.random(-0.35F, 0.35F)
                        : mc.player.getY() + MathUtility.random(0.2F, mc.player.getHeight() + 0.1F);
                double pz = mc.player.getZ() + dir.z * dist + MathUtility.random(-0.35F, 0.35F);
                if (!this.isInBlock(px, py, pz)) {
                    double baseSpeed = 0.075;
                    Vec3d vel = dir.multiply(baseSpeed)
                            .add(MathUtility.random(-0.01F, 0.01F), MathUtility.random(-0.05F, 0.01F), MathUtility.random(-0.01F, 0.01F))
                            .multiply(0.1);
                    this.addParticle(new Vec3d(px, py, pz), vel, (long)MathUtility.random(1500.0, 2000.0), 3.0, 5.0E-5);
                }
            }

            if (this.idle.isSelected()) {
                Vec3d base = mc.player.getPos().add(0.0, mc.player.getHeight() / 2.0, 0.0);
                int n = (int)this.count.getCurrentValue();

                for (int i = 0; i < n; i++) {
                    double dist = MathUtility.random(7.0, 35.0);
                    double angle = Math.toRadians(MathUtility.random(0.0, 360.0));
                    double height = MathUtility.random(-7.0, 25.0);
                    Vec3d spawn = base.add(Math.cos(angle) * dist, height, Math.sin(angle) * dist);
                    if (!this.isInBlock(spawn.x, spawn.y, spawn.z)) {
                        long life = (long)MathUtility.random(1500.0, 2000.0);
                        double spd = Math.random() < 0.8 ? MathUtility.random(0.015F, 0.03F) : 0.125;
                        double phi = Math.toRadians(MathUtility.random(0.0, 360.0));
                        Vec3d vel = new Vec3d(Math.cos(phi) * spd, MathUtility.random((float)(-spd * 0.1), (float)(spd * 0.1)), Math.sin(phi) * spd);
                        this.addParticle(spawn, vel, life, 3.0, 5.0E-5);
                    }
                }
            }

            for (Entity e : mc.world.getEntities()) {
                if (this.pearlReason.isSelected() && e instanceof EnderPearlEntity && e.getVelocity().lengthSquared() > 0.01) {
                    this.spawnTrail(e.getPos(), 2);
                }

                if (this.tridentReason.isSelected() && e instanceof TridentEntity && e.getVelocity().lengthSquared() > 0.01) {
                    this.spawnTrail(e.getPos(), 1);
                }

                if (this.arrowReason.isSelected() && e instanceof ArrowEntity && e.getVelocity().lengthSquared() > 0.01) {
                    this.spawnTrail(e.getPos(), 1);
                }
            }
        }
    };
    private final EventListener<AttackEvent> onAttack = event -> {
        if (this.hit.isSelected()) {
            Entity target = event.getEntity();
            if (target != null) {
                int n = (int)this.hitCount.getCurrentValue();

                for (int i = 0; i < n; i++) {
                    double px = target.getX() + MathUtility.random(-0.4F, 0.4F);
                    double py = target.getY() + MathUtility.random(-0.4F, target.getHeight() + 0.4F);
                    double pz = target.getZ() + MathUtility.random(-0.4F, 0.4F);
                    if (!this.isInBlock(px, py, pz)) {
                        float baseMx = MathUtility.random(-0.8F, 0.8F) * 2.0F;
                        float baseMy = MathUtility.random(-0.25, 1.4F);
                        float baseMz = MathUtility.random(-0.8F, 0.8F) * 2.0F;
                        Vec3d vel = new Vec3d(baseMx * 0.075, baseMy * 0.075, baseMz * 0.075);
                        long life = (long)MathUtility.random(1000.0, 1200.0);
                        this.addParticle(new Vec3d(px, py, pz), vel, life, 0.5, 7.0E-4);
                    }
                }
            }
        }
    };

    private void spawnTrail(Vec3d pos, int count) {
        for (int i = 0; i < (int)(count * 2.5); i++) {
            double angle = Math.toRadians(MathUtility.random(0.0, 360.0));
            double dy = MathUtility.random(0.1F, 0.35F);
            Vec3d spawn = pos.add(0.0, dy, 0.0);
            if (!this.isInBlock(spawn.x, spawn.y, spawn.z)) {
                float speedMin = MathUtility.random(0.015F, 0.0375F);
                float speedMax = MathUtility.random(0.05F, 0.075F);
                double spd = MathUtility.random(speedMin, speedMax);
                double spdY = spd * 0.4;
                double angleVel = Math.toRadians(MathUtility.random(0.0, 360.0));
                Vec3d vel = new Vec3d(Math.cos(angleVel) * spd, MathUtility.random((float)(-spdY), (float)spdY), Math.sin(angleVel) * spd);
                this.addParticle(spawn, vel, (long)MathUtility.random(2400.0, 2800.0), 2.0, 5.0E-5);
            }
        }
    }

    private void addParticle(Vec3d pos, Vec3d vel, long life, double smoothFactor, double gravity) {
        this.particles.add(new Particles.Particle(pos, vel, life, smoothFactor, gravity, this.randomTexture()));
    }

    private void refreshActiveTextures() {
        this.activeTextures.clear();
        if (this.texStar.isSelected()) {
            this.activeTextures.add(id("glowstar.png"));
        }

        if (this.texHeart.isSelected()) {
            this.activeTextures.add(id("heart.png"));
        }

        if (this.texCross.isSelected()) {
            this.activeTextures.add(id("cross.png"));
        }

        if (this.texLightning.isSelected()) {
            this.activeTextures.add(id("lightning.png"));
        }

        if (this.texFirefly.isSelected()) {
            this.activeTextures.add(id("firefly.png"));
        }

        if (this.texSnow.isSelected()) {
            this.activeTextures.add(id("snow.png"));
        }

        if (this.texPumpkin.isSelected()) {
            this.activeTextures.add(id("pumpkin.png"));
        }

        if (this.texDollar.isSelected()) {
            this.activeTextures.add(id("dollar.png"));
        }
    }

    private Identifier randomTexture() {
        this.refreshActiveTextures();
        return this.activeTextures.isEmpty() ? id("glowstar.png") : this.activeTextures.get((int)(Math.random() * this.activeTextures.size()));
    }

    private boolean isInBlock(double x, double y, double z) {
        if (mc.world == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(BlockPos.ofFloored(x, y, z));
        return !state.isAir() && state.isSolid();
    }

    private boolean isMoving() {
        return mc.player.input.movementForward != 0.0F || mc.player.input.movementSideways != 0.0F;
    }

    private static Identifier id(String file) {
        return Identifier.of("rocksnow", "textures/particles/" + file);
    }

    private static void writeQuad(BufferBuilder buf, Matrix4f mat, Vec3d pos, Vec3d cam, Vector3f right, Vector3f up, float s, int r, int g, int b, int a) {
        float rx = right.x * s;
        float ry = right.y * s;
        float rz = right.z * s;
        float ux = up.x * s;
        float uy = up.y * s;
        float uz = up.z * s;
        float dx = (float)(pos.x - cam.x);
        float dy = (float)(pos.y - cam.y);
        float dz = (float)(pos.z - cam.z);
        buf.vertex(mat, dx - rx - ux, dy - ry - uy, dz - rz - uz).texture(0.0F, 1.0F).color(r, g, b, a);
        buf.vertex(mat, dx + rx - ux, dy + ry - uy, dz + rz - uz).texture(1.0F, 1.0F).color(r, g, b, a);
        buf.vertex(mat, dx + rx + ux, dy + ry + uy, dz + rz + uz).texture(1.0F, 0.0F).color(r, g, b, a);
        buf.vertex(mat, dx - rx + ux, dy - ry + uy, dz - rz + uz).texture(0.0F, 0.0F).color(r, g, b, a);
    }

    private static Vec3d lerp3(Vec3d a, Vec3d b, float t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t
        );
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Override
    public void onDisable() {
        this.particles.clear();
        super.onDisable();
    }

    @Environment(EnvType.CLIENT)
    static class Particle {
        Vec3d pos;
        Vec3d prevPos;
        Vec3d vel;
        final long lifeTime;
        final long startTime;
        final double smoothFactor;
        final double gravity;
        final Identifier texture;
        final Timer timer = new Timer();
        final Animation alpha = new Animation(400L, Easing.FIGMA_EASE_IN_OUT);
        private long lastUpdateNs;
        private boolean dying = false;
        final boolean hasCustomColor;
        final int customR;
        final int customG;
        final int customB;

        Particle(Vec3d pos, Vec3d vel, long lifeTime, double smoothFactor, double gravity, Identifier texture) {
            this(pos, vel, lifeTime, smoothFactor, gravity, texture, -1, -1, -1);
        }

        Particle(Vec3d pos, Vec3d vel, long lifeTime, double smoothFactor, double gravity, Identifier texture, int customR, int customG, int customB) {
            this.pos = pos;
            this.prevPos = pos;
            this.vel = vel;
            this.lifeTime = lifeTime;
            this.startTime = System.currentTimeMillis();
            this.smoothFactor = smoothFactor;
            this.gravity = gravity;
            this.texture = texture;
            this.lastUpdateNs = System.nanoTime();
            this.alpha.update(1.0F);
            this.hasCustomColor = customR >= 0;
            this.customR = customR;
            this.customG = customG;
            this.customB = customB;
        }

        void tick() {
            if (!this.timer.finished(this.lifeTime)) {
                this.alpha.update(1.0F);
            } else if (!this.dying) {
                this.dying = true;
                this.alpha.update(0.0F);
            } else {
                this.alpha.update(0.0F);
            }

            long nowNs = System.nanoTime();
            double delta = (nowNs - this.lastUpdateNs) / 1.0E9 * 60.0;
            this.lastUpdateNs = nowNs;
            this.prevPos = this.pos;
            float progress = Math.min(1.0F, (float)(System.currentTimeMillis() - this.startTime) / (float)this.lifeTime);
            double factor = Math.pow(1.0 - progress, this.smoothFactor);
            this.pos = this.pos.add(this.vel.x * factor * delta, this.vel.y * factor * delta, this.vel.z * factor * delta);
            this.vel = new Vec3d(this.vel.x * 0.9999, this.vel.y * 0.9999 - this.gravity, this.vel.z * 0.9999);
        }
    }
}