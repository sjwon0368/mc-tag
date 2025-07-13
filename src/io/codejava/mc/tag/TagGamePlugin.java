/* 
 * 
 */

package io.codejava.mc.tag;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.key.Keyed; // build.yml에 net.kyori.adventure.key 라이브러리 추가

import java.time.Duration;
import java.util.*;

public class TagGamePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Player tagger;
    private Player runner;
    private Location taggerOriginalLoc;
    private Location runnerOriginalLoc;
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private boolean gameRunning = false;
    private BukkitRunnable taggerTrackerTask; // taggerTrackerTask 라고 써져 있어도 도망자 위치 추적임
    private final Set<UUID> frozen = new HashSet<>();

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("tagstart")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning || tagger == null || runner == null || !tagger.isOnline()) return;

                ItemStack offhandItem = tagger.getInventory().getItemInOffHand();
                if (offhandItem != null && offhandItem.getType() == Material.DIAMOND_ORE && offhandItem.getAmount() > 0) {
                    offhandItem.setAmount(offhandItem.getAmount() - 1);
                    if (offhandItem.getAmount() > 0) {
                        tagger.getInventory().setItemInOffHand(offhandItem);
                    } else {
                        tagger.getInventory().setItemInOffHand(null);
                    }
                    startTrackingRunner();
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            if (!event.getFrom().toVector().equals(event.getTo().toVector())) {
                event.setTo(event.getFrom());
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (frozen.contains(event.getPlayer().getUniqueId())) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
                    event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 128, false, false, false));
                }
            }.runTaskLater(this, 1L);
        }
    }

    private void freezePlayer(Player player) {
        frozen.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 128, false, false, false));
    }

    private void unfreezePlayer(Player player) {
        frozen.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private BukkitRunnable runnerTrackerTask; // 도망자에게 2분마다 술래 위치 표시하고 30초 뒤에 숨기는 거

    private void startRunnerTracker() { // startRunnerTracker 라고 써져 있어도 어쨌든 술래 위치 추적임
        runnerTrackerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning || runner == null || tagger == null || !runner.isOnline() || !tagger.isOnline()) {
                    cancel();
                    runnerTrackerTask = null;
                    return;
                }
                // Start 30 seconds of tracking
                new BukkitRunnable() {
                    int seconds = 30;
                    @Override
                    public void run() {
                        if (!gameRunning || runner == null || tagger == null || !runner.isOnline() || !tagger.isOnline() || seconds <= 0) {
                            cancel();
                            return;
                        }
                        Location loc = tagger.getLocation();
                        String worldSuffix = switch (loc.getWorld().getEnvironment()) {
                            case NETHER -> " §e(네더)";
                            case THE_END -> " §e(엔드)";
                            default -> "";
                        };
                        String coords = "술래 좌표:\n" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + worldSuffix
                            + "\n남은 시간: " + seconds + "초";
                        runner.sendActionBar(Component.text(coords, NamedTextColor.WHITE));
                        seconds--;
                    }
                }.runTaskTimer(this, 0, 20);
            }
        };
        // Run every 2 minutes (2400 ticks)
        runnerTrackerTask.runTaskTimer(this, 0, 2400);
    }

// Call startRunnerTracker() when the game starts

    private void startTrackingRunner() {
        if (taggerTrackerTask != null) {
            tagger.sendMessage("§e이미 도망자 추적이 활성화되어 있습니다.");
            return;
        }

        taggerTrackerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameRunning || tagger == null || runner == null || !tagger.isOnline() || !runner.isOnline()) {
                    cancel();
                    return;
                }

                Location loc = runner.getLocation();
                String worldSuffix = switch (loc.getWorld().getEnvironment()) {
                    case NETHER -> " §e(네더)";
                    case THE_END -> " §e(엔드)";
                    default -> "";
                };

                String coords = "도망자 좌표:\n" + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + worldSuffix;

                tagger.sendActionBar(Component.text(coords, NamedTextColor.WHITE));
            }
        };

        taggerTrackerTask.runTaskTimer(this, 0, 20);
    }

    @EventHandler
    public void onTaggerDeath(PlayerDeathEvent event) {
        if (!gameRunning) return;
        if (event.getEntity().equals(tagger)) {
            if (taggerTrackerTask != null) {
                taggerTrackerTask.cancel();
                taggerTrackerTask = null;
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.isOp()) {
            p.sendMessage("§c이 명령어를 사용할 권한이 없습니다."); // op 유저만 사용 - 커맨드
            return true;
        }

        if (args.length != 2) {
            p.sendMessage("§c명령어 사용 방법:\n/tagstart <술래> <도망자>\n예시: /tagstart user1 user2"); // 도움말 출력
            return true;
        }

        var tag = Bukkit.getPlayerExact(args[0]);
        var run = Bukkit.getPlayerExact(args[1]); // 플레이어 데이터 불러오기

        if (tag == null || run == null || !tag.isOnline() || !run.isOnline()) {
            p.sendMessage("§c오류: 게임 시작 실패. 입력한 플레이어 이름 중 하나 이상이 존재하지 않거나 오프라인입니다."); // 오류 1 - 플레이어 존재하지 않음
            return true;
        }

        if (tag.equals(run)) {
            p.sendMessage("§c술래와 도망자가 동일할 수 없습니다."); // 오류 2 - 술래, 도망자 아이디가 같음
            return true;
        }

        tagger = tag;
        runner = run;
        taggerOriginalLoc = tag.getLocation(); // 게임 시작 전 좌표 불러오기기
        runnerOriginalLoc = run.getLocation();

        double distance = tag.getLocation().distance(run.getLocation());
        if (distance < 100) {
            p.sendMessage("§e두 플레이어 사이의 거리가 100블럭 이상이 아닙니다. 자동으로 랜덤 위치에 100블럭 이상의 거리로 순간이동합니다.");
            tag.teleport(tag.getWorld().getSpawnLocation().add(0, 2, 0)); // 100블럭 거리 내에 있는 경우 렌덤 텔포
            run.teleport(tag.getWorld().getSpawnLocation().add(150, 2, 0));
        }

        freezePlayer(tag);
        freezePlayer(run);

        tag.showTitle(Title.title(Component.text("술래잡기가 곧 시작됩니다.", NamedTextColor.YELLOW), Component.text(""),
                Title.Times.of(Duration.ofSeconds(1), Duration.ofSeconds(15), Duration.ofSeconds(1)))); // 게임 시작 안내

        new BukkitRunnable() {
            int seconds = 15;
            @Override
            public void run() {
                if (seconds <= 0) {
                    unfreezePlayer(tagger);
                    unfreezePlayer(runner);
                    tagger.sendActionBar(Component.text("게임 시작!", NamedTextColor.GREEN)); // 시작 알림
                    runner.sendActionBar(Component.text("게임 시작!", NamedTextColor.GREEN));
                    gameRunning = true;
                    cancel();
                    return;
                }
                String msg = "게임 시작까지 " + seconds + "초";
                tagger.sendActionBar(Component.text(msg)); // 핫바 위에 게임 시작까지 대기시간 표시시
                runner.sendActionBar(Component.text(msg));
                seconds--;
            }
        }.runTaskTimer(this, 0, 20);

        return true;
    }


    

    

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (!gameRunning) return;
        if (e.getPlayer().equals(tagger) || e.getPlayer().equals(runner)) {
            gameRunning = false;
            Bukkit.broadcastMessage("§c오류: 하나 이상의 플레이어가 게임을 나갔습니다. 게임이 종료됩니다."); // 플레이어 나감 - 게임 종료
            if (tagger != null) {
                unfreezePlayer(tagger);
                if (taggerOriginalLoc != null) tagger.teleport(taggerOriginalLoc);
            }
            if (runner != null) {
                unfreezePlayer(runner);
                if (runnerOriginalLoc != null) runner.teleport(runnerOriginalLoc);
            }
            if (taggerTrackerTask != null) {
                taggerTrackerTask.cancel();
                taggerTrackerTask = null;
            }
            if (runnerTrackerTask != null) {
                runnerTrackerTask.cancel();
                runnerTrackerTask = null;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!gameRunning) return;
        Player p = e.getEntity();

        if (p.equals(tagger)) {
            if (taggerTrackerTask != null) {
                taggerTrackerTask.cancel();
                taggerTrackerTask = null;
            }
            Bukkit.broadcastMessage("§e술래가 사망했습니다. 1분 후 동일한 위치에서 리스폰됩니다."); // 술래 사망 - 1분 후 리스폰
            deathLocations.put(p.getUniqueId(), p.getLocation());
            scheduleRespawn(p, 60);
        } else if (p.equals(runner)) {
            if (runnerTrackerTask != null) {
                runnerTrackerTask.cancel();
                runnerTrackerTask = null;
            }
            Bukkit.broadcastMessage("§e도망자가 사망했습니다. 2분 후 동일한 위치에서 리스폰됩니다."); // 도망자 사망 - 2분 후 리스폰
            deathLocations.put(p.getUniqueId(), p.getLocation());
            scheduleRespawn(p, 120);
        }
    }

    @EventHandler
    public void onDragonKill(EntityDeathEvent e) {
        if (!gameRunning) return;
        if (e.getEntity() instanceof EnderDragon) {
            if (e.getEntity().getKiller() == null || e.getEntity().getKiller().equals(tagger)) {
                Bukkit.broadcastMessage("§c도망자의 승리 조건 실패. 게임 종료.");
            } else {
                Bukkit.broadcastMessage("§b도망자 승! 게임 종료"); // 승리 조건 확인 - 도망자
            }
            endGame();
        }
    }

    @EventHandler
    public void onRunnerKilled(EntityDeathEvent e) {
        if (!gameRunning) return;
        if (e.getEntity().equals(runner) && e.getEntity().getKiller() != null && e.getEntity().getKiller().equals(tagger)) {
            Bukkit.broadcastMessage("§a술래 승! 게임 종료"); // 승리 조건 확인 - 술래래
            endGame();
        }
    }

    @EventHandler
    public void onDragonDamaged(EntityDamageByEntityEvent event) {
        if (!gameRunning) return;
        if (event.getEntity() instanceof EnderDragon && event.getDamager() instanceof Player attacker) {
            if (attacker.equals(tagger)) {
                attacker.sendMessage("§c술래는 엔더 드래곤을 공격할 수 없습니다!"); // 술래는 엔더드레곤 공격 불가
                event.setCancelled(true);
            }
        }
    }

    private void scheduleRespawn(Player p, int seconds) {
        new BukkitRunnable() {
            int timer = seconds;
            @Override
            public void run() {
                if (timer <= 0) {
                    Location loc = deathLocations.remove(p.getUniqueId());
                    // Wait for player to respawn, then teleport
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (loc != null && p.isOnline()) {
                                p.teleport(loc);
                            }
                        }
                    }.runTaskLater(TagGamePlugin.this, 1);
                    cancel();
                    return;
                }
                p.sendTitle("§c죽었습니다!", "§f리스폰까지 " + timer + "초", 0, 20, 0);
                timer--;
            }
        }.runTaskTimer(this, 20, 20);
    }

    private void endGame() {
        gameRunning = false;
        if (taggerTrackerTask != null) {
            taggerTrackerTask.cancel();
            taggerTrackerTask = null;
        }
        if (runnerTrackerTask != null) {
        runnerTrackerTask.cancel();
        runnerTrackerTask = null;
        }
        if (tagger != null && tagger.isOnline() && taggerOriginalLoc != null) {
            unfreezePlayer(tagger);
            tagger.teleport(taggerOriginalLoc);
        }
        if (runner != null && runner.isOnline() && runnerOriginalLoc != null) {
            unfreezePlayer(runner);
            runner.teleport(runnerOriginalLoc);
        } // 게임 종료시 술래, 도망자 원래 위치로 텔레포트
    }
}
