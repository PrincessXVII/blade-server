package dev.systemcrash.blade.weapons.legacy.weapons.villagerstaff;

import dev.systemcrash.blade.weapons.legacy.LegacyDemoraBridge;
import dev.systemcrash.blade.weapons.legacy.TrueDamageUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display.Brightness;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VillagerStaff {
   private final LegacyDemoraBridge plugin;
   private final Map<UUID, Long> cooldowns;
   private final Map<UUID, Long> taggedForReward;
   private final Map<UUID, Location> activeExplosions;

   public VillagerStaff(LegacyDemoraBridge plugin) {
      this.plugin = plugin;
      this.cooldowns = new HashMap<>();
      this.taggedForReward = new ConcurrentHashMap<>();
      this.activeExplosions = new ConcurrentHashMap<>();
   }

   public void sendCooldownMessage(Player player) {
      long remaining = this.getRemainingCooldown(player);
      player.sendMessage(
         this.plugin.getMessageWithPrefix("weapon-messages.villager-staff-cooldown").replace("{time}", String.valueOf(remaining))
      );
   }

   public ItemStack createVillagerStaff() {
      ItemStack staff = new ItemStack(Material.IRON_AXE);
      ItemMeta meta = staff.getItemMeta();
      if (meta != null) {
         meta.displayName(this.plugin.getLanguageManager().getWeaponDisplayName("villager_staff"));
         meta.setCustomModelData(3);
         meta.setUnbreakable(true);
         meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
         staff.setItemMeta(meta);
      }

      return staff;
   }

   public boolean isVillagerStaff(ItemStack item) {
      if (item != null && item.getType() == Material.IRON_AXE) {
         ItemMeta meta = item.getItemMeta();
         return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 3;
      } else {
         return false;
      }
   }

   public boolean isOnCooldown(Player player) {
      UUID playerId = player.getUniqueId();
      if (!this.cooldowns.containsKey(playerId)) {
         return false;
      } else {
         long cooldownTime = this.plugin.getConfig().getLong("weapons.villager_staff.cooldown.time", 10L) * 1000L;
         long lastUse = this.cooldowns.get(playerId);
         return System.currentTimeMillis() - lastUse < cooldownTime;
      }
   }

   public long getRemainingCooldown(Player player) {
      UUID playerId = player.getUniqueId();
      if (!this.cooldowns.containsKey(playerId)) {
         return 0L;
      } else {
         long cooldownTime = this.plugin.getConfig().getLong("weapons.villager_staff.cooldown.time", 10L) * 1000L;
         long lastUse = this.cooldowns.get(playerId);
         long remaining = cooldownTime - (System.currentTimeMillis() - lastUse);
         return Math.max(0L, remaining / 1000L);
      }
   }

   public void setCooldown(Player player) {
      this.cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
   }

   public void clearCooldowns(UUID playerId) {
      this.cooldowns.remove(playerId);
   }

   public void useStaff(Player player) {
      Location start = player.getEyeLocation().clone();
      Vector direction = start.getDirection().normalize();
      World world = player.getWorld();
      double maxRange = this.getMaxRange();
      Predicate<Entity> targetFilter = entity -> entity instanceof Player target
            && !target.equals(player)
            && !target.isDead();
      RayTraceResult entityHit = world.rayTraceEntities(start, direction, maxRange, 0.5, targetFilter);
      RayTraceResult blockHit = world.rayTraceBlocks(start, direction, maxRange, FluidCollisionMode.NEVER, true);

      double entityDistance = entityHit != null && entityHit.getHitEntity() != null
            ? start.distance(entityHit.getHitPosition().toLocation(world))
            : Double.MAX_VALUE;
      double blockDistance = blockHit != null && blockHit.getHitBlock() != null
            ? start.distance(blockHit.getHitPosition().toLocation(world))
            : Double.MAX_VALUE;

      Location landing;
      Player directHit = null;
      if (entityDistance <= blockDistance && entityHit != null && entityHit.getHitEntity() instanceof Player hitPlayer) {
         landing = hitPlayer.getLocation().clone();
         directHit = hitPlayer;
      } else if (blockHit != null && blockHit.getHitBlock() != null) {
         landing = blockHit.getHitPosition().toLocation(world);
      } else {
         landing = start.clone().add(direction.clone().multiply(maxRange));
      }

      this.createSkyBeamStrike(player, landing, directHit);
   }

   private double getMaxRange() {
      return this.plugin.getConfig().getDouble("weapons.villager_staff.max_range", 50.0);
   }

   private void createSkyBeamStrike(Player attacker, Location landing, Player directHit) {
      double heightOffset = 1.5;
      Location displayLocation = landing.clone().add(0.0, heightOffset, 0.0);
      ItemDisplay itemDisplay = (ItemDisplay) displayLocation.getWorld().spawnEntity(displayLocation, EntityType.ITEM_DISPLAY);
      this.setupItemDisplay(itemDisplay);
      this.scheduleGrowExplodeShrink(itemDisplay, attacker, landing, directHit);
   }

   private void setupItemDisplay(ItemDisplay itemDisplay) {
      ItemStack slimeBall = this.createSlimeBallHelmet();
      itemDisplay.setItemStack(slimeBall);
      float startX = 1.0F;
      float startY = 50.0F;
      float startZ = 1.0F;
      Vector3f translation = new Vector3f(0.0F, 0.0F, 0.0F);
      AxisAngle4f leftRotation = new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F);
      Vector3f scale = new Vector3f(startX, startY, startZ);
      AxisAngle4f rightRotation = new AxisAngle4f(0.0F, 0.0F, 0.0F, 1.0F);
      Transformation transformation = new Transformation(translation, new Quaternionf(leftRotation), scale, new Quaternionf(rightRotation));
      itemDisplay.setTransformation(transformation);
      itemDisplay.setGravity(false);
      itemDisplay.setInvulnerable(true);
      itemDisplay.setBrightness(new Brightness(15, 15));
   }

   private ItemStack createSlimeBallHelmet() {
      ItemStack slimeBall = new ItemStack(Material.SLIME_BALL);
      ItemMeta meta = slimeBall.getItemMeta();
      if (meta != null) {
         meta.setCustomModelData(12);
         slimeBall.setItemMeta(meta);
      }

      return slimeBall;
   }

   private void scheduleGrowExplodeShrink(ItemDisplay itemDisplay, Player attacker, Location landing, Player directHit) {
      final int growTicks = 12;
      final int shrinkTicks = 12;
      final float startX = 1.0F;
      final float startY = 50.0F;
      final float startZ = 1.0F;
      final float endX = 4.0F;
      final float endY = 50.0F;
      final float endZ = 4.0F;
      (new BukkitRunnable() {
         int tick = 0;
         boolean exploded = false;
         float yaw = 0.0F;

         public void run() {
            if (!itemDisplay.isDead() && itemDisplay.isValid()) {
               this.yaw += 10.0F;
               if (this.yaw >= 360.0F) {
                  this.yaw -= 360.0F;
               }

               AxisAngle4f leftRot = new AxisAngle4f((float) Math.toRadians(this.yaw), 0.0F, 1.0F, 0.0F);
               if (!this.exploded) {
                  float t = Math.min(1.0F, growTicks <= 0 ? 1.0F : (float) this.tick / growTicks);
                  float sx = VillagerStaff.this.lerp(startX, endX, t);
                  float sy = VillagerStaff.this.lerp(startY, endY, t);
                  float sz = VillagerStaff.this.lerp(startZ, endZ, t);
                  VillagerStaff.this.applyTransform(itemDisplay, leftRot, sx, sy, sz);
                  this.tick++;
                  if (t >= 1.0F) {
                     VillagerStaff.this.explodeAtLanding(landing, attacker, directHit);
                     this.exploded = true;
                     this.tick = 0;
                  }
               } else {
                  float t = Math.min(1.0F, shrinkTicks <= 0 ? 1.0F : (float) this.tick / shrinkTicks);
                  float sx = VillagerStaff.this.lerp(endX, startX, t);
                  float sy = VillagerStaff.this.lerp(endY, startY, t);
                  float sz = VillagerStaff.this.lerp(endZ, startZ, t);
                  VillagerStaff.this.applyTransform(itemDisplay, leftRot, sx, sy, sz);
                  this.tick++;
                  if (t >= 1.0F) {
                     itemDisplay.remove();
                     this.cancel();
                  }
               }
            } else {
               this.cancel();
            }
         }
      }).runTaskTimer(this.plugin.getHost(), 0L, 1L);
   }

   private void explodeAtLanding(Location landing, Player attacker, Player directHit) {
      World world = landing.getWorld();
      if (world == null) {
         return;
      }

      float power = (float) this.plugin.getConfig().getDouble("weapons.villager_staff.explosion.power", 4.0);
      boolean breakBlocks = this.plugin.getConfig().getBoolean("weapons.villager_staff.explosion.break-blocks", true);
      boolean setFire = this.plugin.getConfig().getBoolean("weapons.villager_staff.explosion.set-fire", false);
      double blastRadius = this.plugin.getConfig().getDouble("weapons.villager_staff.explosion.blast-radius", 4.0);
      double trueDamage = this.plugin.getConfig().getDouble("weapons.villager_staff.explosion.true-damage", 21.0);

      if (attacker != null) {
         this.activeExplosions.put(attacker.getUniqueId(), landing.clone());
         this.plugin.getHost().getServer().getScheduler().runTaskLater(
               this.plugin.getHost(),
               () -> this.activeExplosions.remove(attacker.getUniqueId()),
               2L
         );
      }

      if (attacker != null && attacker.isOnline()) {
         this.setCooldown(attacker);
      }

      // Vanilla explode only — no guardian_hit4 / custom staff SFX.
      world.playSound(landing, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0F, 0.85F);
      world.createExplosion(landing, power, setFire, breakBlocks, attacker);
      world.spawnParticle(Particle.EXPLOSION_EMITTER, landing, 1);
      world.spawnParticle(Particle.EXPLOSION, landing, 40, blastRadius / 2.0, blastRadius / 2.0, blastRadius / 2.0, 0.1);

      for (LivingEntity entity : world.getNearbyLivingEntities(landing, blastRadius)) {
         if (attacker != null && entity.equals(attacker)) {
            continue;
         }
         if (entity instanceof Player) {
            this.taggedForReward.put(entity.getUniqueId(), System.currentTimeMillis());
         }
         TrueDamageUtil.dealTrueDamage(entity, trueDamage, attacker);
      }

      if (directHit != null && !directHit.equals(attacker)) {
         this.taggedForReward.put(directHit.getUniqueId(), System.currentTimeMillis());
      }
   }

   /** Stop only guardian hurt — never stopAllSounds (that muted legendary weapons). */
   public static void stopDeferredExplosionSounds(Player player) {
      if (player == null || !player.isOnline()) {
         return;
      }
      player.stopSound(Sound.ENTITY_GUARDIAN_HURT);
   }

   public boolean shouldCancelExplosionDamage(Location damageLocation, DamageCause cause) {
      if (cause != DamageCause.BLOCK_EXPLOSION && cause != DamageCause.ENTITY_EXPLOSION) {
         return false;
      }
      for (Location explosionLocation : this.activeExplosions.values()) {
         if (explosionLocation.getWorld() != null
               && explosionLocation.getWorld().equals(damageLocation.getWorld())
               && explosionLocation.distance(damageLocation) <= 6.0) {
            return true;
         }
      }
      return false;
   }

   public boolean isTaggedForReward(UUID entityId) {
      Long ts = this.taggedForReward.get(entityId);
      return ts != null && System.currentTimeMillis() - ts <= 3000L;
   }

   public void removeRewardTag(UUID entityId) {
      this.taggedForReward.remove(entityId);
   }

   private void applyTransform(ItemDisplay itemDisplay, AxisAngle4f leftRotation, float x, float y, float z) {
      Transformation current = itemDisplay.getTransformation();
      Transformation updated = new Transformation(
            current.getTranslation(), new Quaternionf(leftRotation), new Vector3f(x, y, z), current.getRightRotation()
      );
      itemDisplay.setTransformation(updated);
   }

   private float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   public Vector prepareStaffDirection(Location loc) {
      if (loc == null) {
         return null;
      } else {
         Vector v = loc.getDirection().setY(0);
         return v.lengthSquared() < 1.0E-6 ? null : v.normalize();
      }
   }
}
