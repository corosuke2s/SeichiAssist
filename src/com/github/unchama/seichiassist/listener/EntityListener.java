package com.github.unchama.seichiassist.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;

import com.github.unchama.seichiassist.ActiveSkill;
import com.github.unchama.seichiassist.ActiveSkillEffect;
import com.github.unchama.seichiassist.SeichiAssist;
import com.github.unchama.seichiassist.data.BreakArea;
import com.github.unchama.seichiassist.data.Coordinate;
import com.github.unchama.seichiassist.data.Mana;
import com.github.unchama.seichiassist.data.PlayerData;
import com.github.unchama.seichiassist.util.Util;

public class EntityListener implements Listener {
	SeichiAssist plugin = SeichiAssist.plugin;
	HashMap<UUID,PlayerData> playermap = SeichiAssist.playermap;

	@EventHandler
	public void onPlayerActiveSkillEvent(ProjectileHitEvent event){
		//矢を取得する
		final Projectile e = event.getEntity();
		ProjectileSource projsource;
		Player player;


		if(!e.hasMetadata("ArrowSkill")) {
			return;
		}
		Projectile proj = (Projectile)e;
    	projsource = proj.getShooter();
		if(!(projsource instanceof Player)){
			return;
		}
		player = (Player)projsource;

		if(SeichiAssist.DEBUG){
			player.sendMessage(ChatColor.RED + "ProjectileHitEventの呼び出し");
		}

		//もしサバイバルでなければ処理を終了
		//もしフライ中なら終了
		if(!player.getGameMode().equals(GameMode.SURVIVAL) || player.isFlying()){
			return;
		}


		//壊されるブロックを取得
		Block block = null;
		block = player.getWorld().getBlockAt(proj.getLocation().add(proj.getVelocity().normalize()));


		//他人の保護がかかっている場合は処理を終了
		if(!Util.getWorldGuard().canBuild(player, block.getLocation())){
			return;
		}

		//ブロックのタイプを取得
		Material material = block.getType();

		//ブロックタイプがmateriallistに登録されていなければ処理終了
		if(!SeichiAssist.materiallist.contains(material) && e.hasMetadata("ArrowSkill")){
			return;
		}

		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//UUIDを基にプレイヤーデータ取得
		PlayerData playerdata = SeichiAssist.playermap.get(uuid);
		//念のためエラー分岐
		if(playerdata == null){
			player.sendMessage(ChatColor.RED + "playerdataがありません。管理者に報告してください");
			plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "SeichiAssist[blockbreaklistener処理]でエラー発生");
			plugin.getLogger().warning(player.getName() + "のplayerdataがありません。開発者に報告してください");
			return;
		}
		ActiveSkill[] activeskill = ActiveSkill.values();
		String worldname = "world_sw";
		if(SeichiAssist.DEBUG){
			worldname = "world";
		}
		if(player.getWorld().getName().equalsIgnoreCase(worldname)){
			if(Util.getGravity(player, block, activeskill[playerdata.activeskilldata.skilltype-1].getBreakLength(playerdata.activeskilldata.skillnum).y, 1) > 3){
				player.sendMessage(ChatColor.RED + "整地ワールドでは必ず上から掘ってください。");
				return;
			}
		}






		//プレイヤーインベントリを取得
		PlayerInventory inventory = player.getInventory();
		//メインハンドとオフハンドを取得
		ItemStack mainhanditem = inventory.getItemInMainHand();
		ItemStack offhanditem = inventory.getItemInOffHand();
		//実際に使用するツールを格納する
		ItemStack tool = null;
		//メインハンドにツールがあるか
		boolean mainhandtoolflag = SeichiAssist.breakmateriallist.contains(mainhanditem.getType());
		//オフハンドにツールがあるか
		boolean offhandtoolflag = SeichiAssist.breakmateriallist.contains(offhanditem.getType());

		//場合分け
		if(mainhandtoolflag){
			//メインハンドの時
			tool = mainhanditem;
		}else if(offhandtoolflag){
			//サブハンドの時
			return;
		}else{
			//どちらにももっていない時処理を終了
			return;
		}
		//耐久値がマイナスかつ耐久無限ツールでない時処理を終了
		if(tool.getDurability() > tool.getType().getMaxDurability() && !tool.getItemMeta().spigot().isUnbreakable()){
			return;
		}
		for(Block b : playerdata.activeskilldata.blocklist){
			//スキルで破壊されるブロックの時処理を終了
			if(b.equals(block)){
				if(SeichiAssist.DEBUG){
					player.sendMessage("スキルで使用中のブロックです。");
				}
				return;
			}
		}

		runArrowSkillofHitBlock(player,proj, block, tool);

		proj.remove();
	}


	private void runArrowSkillofHitBlock(Player player,Projectile proj,
			Block block, ItemStack tool) {
		//UUIDを取得
		UUID uuid = player.getUniqueId();
		//playerdataを取得
		PlayerData playerdata = playermap.get(uuid);
		//レベルを取得
		int skilllevel = playerdata.activeskilldata.skillnum;
		//マナを取得
		Mana mana = playerdata.activeskilldata.mana;
		//元ブロックのマテリアルを取得
		Material material = block.getType();
		//元ブロックの真ん中の位置を取得
		Location centerofblock = block.getLocation().add(0.5, 0.5, 0.5);

		//壊されるブロックの宣言
		Block breakblock;
		BreakArea area = playerdata.activeskilldata.area;
		//現在のプレイヤーの向いている方向
		String dir = Util.getCardinalDirection(player);
		//もし前回とプレイヤーの向いている方向が違ったら範囲を取り直す
		if(!dir.equals(area.getDir())){
			area.setDir(dir);
			area.makeArea();
		}
		Coordinate start = area.getStartList().get(0);
		Coordinate end = area.getEndList().get(0);

		//エフェクト用に壊されるブロック全てのリストデータ
		List<Block> breaklist = new ArrayList<Block>();

		//壊される溶岩のリストデータ
		List<Block> lavalist = new ArrayList<Block>();

		//一回の破壊の範囲
		final Coordinate breaklength = area.getBreakLength();
		//１回の全て破壊したときのブロック数
		final int ifallbreaknum = (breaklength.x * breaklength.y * breaklength.z);


		for(int x = start.x ; x <= end.x ; x++){
			for(int z = start.z ; z <= end.z ; z++){
				for(int y = start.y; y <= end.y ; y++){

					breakblock = block.getRelative(x, y, z);
					//player.sendMessage("x:" + x + "y:" + y + "z:" + z + "Type:"+ breakblock.getType().name());
					//もし壊されるブロックがもともとのブロックと同じ種類だった場合
					if(breakblock.getType().equals(material)
							|| (block.getType().equals(Material.DIRT)&&breakblock.getType().equals(Material.GRASS))
							|| (block.getType().equals(Material.GRASS)&&breakblock.getType().equals(Material.DIRT))
							|| (block.getType().equals(Material.GLOWING_REDSTONE_ORE)&&breakblock.getType().equals(Material.REDSTONE_ORE))
							|| (block.getType().equals(Material.REDSTONE_ORE)&&breakblock.getType().equals(Material.GLOWING_REDSTONE_ORE))
							|| breakblock.getType().equals(Material.STATIONARY_LAVA)
							){
						if(Util.canBreak(player, breakblock)){
							if(breakblock.getType().equals(Material.STATIONARY_LAVA)){
								lavalist.add(breakblock);
							}else{
								breaklist.add(breakblock);
								playerdata.activeskilldata.blocklist.add(breakblock);
							}
						}

					}
				}
			}
		}


		//重力値計算
		double gravity = Util.getGravity(player,block,end.y,1);


		//減る経験値計算
		//実際に破壊するブロック数  * 全てのブロックを破壊したときの消費経験値÷すべての破壊するブロック数 * 重力

		double useMana = (double) (breaklist.size()) * gravity
				* ActiveSkill.getActiveSkillUseExp(playerdata.activeskilldata.skilltype, playerdata.activeskilldata.skillnum)
				/(ifallbreaknum) ;
		if(SeichiAssist.DEBUG){
			player.sendMessage(ChatColor.RED + "必要経験値：" + ActiveSkill.getActiveSkillUseExp(playerdata.activeskilldata.skilltype, playerdata.activeskilldata.skillnum));
			player.sendMessage(ChatColor.RED + "全ての破壊数：" + (ifallbreaknum));
			player.sendMessage(ChatColor.RED + "実際の破壊数：" + breaklist.size());
			player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なマナ：" + useMana);
		}
		//減る耐久値の計算
		short durability = (short) (tool.getDurability() + Util.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),breaklist.size()));
		//１マス溶岩を破壊するのにはブロック１０個分の耐久が必要
		durability += Util.calcDurability(tool.getEnchantmentLevel(Enchantment.DURABILITY),10*lavalist.size());


		//重力値の判定
		if(gravity > 15){
			player.sendMessage(ChatColor.RED + "スキルを使用するには上から掘ってください。");
			playerdata.activeskilldata.blocklist.removeAll(breaklist);
			return;
		}

		//実際に経験値を減らせるか判定
		if(!mana.hasMana(useMana)){
			//デバッグ用
			if(SeichiAssist.DEBUG){
				player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なマナが足りません");
			}
			playerdata.activeskilldata.blocklist.removeAll(breaklist);
			return;
		}
		if(SeichiAssist.DEBUG){
			player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なツールの耐久値:" + durability);
		}
		//実際に耐久値を減らせるか判定
		if(tool.getType().getMaxDurability() <= durability && !tool.getItemMeta().spigot().isUnbreakable()){
			//デバッグ用
			if(SeichiAssist.DEBUG){
				player.sendMessage(ChatColor.RED + "アクティブスキル発動に必要なツールの耐久値が足りません");
			}
			playerdata.activeskilldata.blocklist.removeAll(breaklist);
			return;
		}


		//経験値を減らす
		mana.decreaseMana(useMana,player,playerdata.level);

		//耐久値を減らす
		tool.setDurability(durability);




		//以降破壊する処理

		//溶岩を破壊する処理
		for(int lavanum = 0 ; lavanum <lavalist.size();lavanum++){
			lavalist.get(lavanum).setType(Material.AIR);
		}


		//選択されたブロックを破壊する処理

		//エフェクトが指定されていないときの処理
		if(playerdata.activeskilldata.effectnum == 0){
			for(Block b:breaklist){
				Util.BreakBlock(player, b, player.getLocation(), tool,true);
				playerdata.activeskilldata.blocklist.remove(b);
			}
		}
		//エフェクトが指定されているときの処理
		else{
			ActiveSkillEffect[] skilleffect = ActiveSkillEffect.values();
			skilleffect[playerdata.activeskilldata.effectnum - 1].runBreakEffect(player,playerdata,tool,breaklist, start, end,centerofblock);
		}

	}


	@EventHandler
	public void onEntityExplodeEvent(EntityExplodeEvent event){
		Entity e = event.getEntity();
	    if ( e instanceof Projectile){
	    	if(e.hasMetadata("ArrowSkill") || e.hasMetadata("Effect")){
	    		event.setCancelled(true);
	    	}
	    }

	}


	@EventHandler
	public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event){
		Entity e = event.getDamager();
	    if ( e instanceof Projectile){
	    	if(e.hasMetadata("ArrowSkill") || e.hasMetadata("Effect")){
	    		event.setCancelled(true);
	    	}
	    }
	}

	@EventHandler
	public void PvPToggleEvent(EntityDamageByEntityEvent event){
		Entity damager = event.getDamager();
		Entity entity = event.getEntity();
		if(damager instanceof Player && entity instanceof Player){
			UUID uuid_damager = damager.getUniqueId();
			PlayerData playerdata_damager = playermap.get(uuid_damager);
			//念のためエラー分岐
			if(playerdata_damager == null){
				damager.sendMessage(ChatColor.RED + "playerdataがありません。管理者に報告してください");
				plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "SeichiAssist[PvP処理]でエラー発生");
				plugin.getLogger().warning(damager.getName()+ "のplayerdataがありません。開発者に報告してください");
				return;
			}
			if(!playerdata_damager.pvpflag){
				event.setCancelled(true);
				return;
			}

			UUID uuid_entity = entity.getUniqueId();
			PlayerData playerdata_entity = playermap.get(uuid_entity);
			//念のためエラー分岐
			if(playerdata_entity == null){
				entity.sendMessage(ChatColor.RED + "playerdataがありません。管理者に報告してください");
				plugin.getServer().getConsoleSender().sendMessage(ChatColor.RED + "SeichiAssist[PvP処理]でエラー発生");
				plugin.getLogger().warning(entity.getName()+ "のplayerdataがありません。開発者に報告してください");
				return;
			}
			if(!playerdata_entity.pvpflag){
				event.setCancelled(true);
				return;
			}
		}
	}
}
