package amata1219.lectern;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Lectern extends JavaPlugin implements Listener {

	private static Lectern plugin;
	private static final String SIGN_TITLE = "[Lectern]";
	private static final ItemStack glass = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
	private static Constructor<?> newMinecraftKey;
	private static Constructor<?> newPacketDataSerializer;
	private static Constructor<?> newPacketPlayOutCustomPayload;
	private static Method getHandle;
	private static Field playerConnection;
	private static Method sendPacket;

	static{
		String version = Bukkit.getServer().getClass().getPackage().getName().replaceFirst(".*(\\d+_\\d+_R\\d+).*", "$1");
		String nms = "net.minecraft.server.v" + version;
		Constructor<?> arg0 = null, arg1 = null, arg2 = null;
		Method arg3 = null;
		Field arg4 = null;
		Method arg5 = null;
		try{
			Class<?> MinecraftKey = Class.forName(nms + ".MinecraftKey");
			arg0 = MinecraftKey.getConstructor(String.class);
			Class<?> PacketDataSerializer = Class.forName(nms + ".PacketDataSerializer");
			arg1 = PacketDataSerializer.getConstructor(ByteBuf.class);
			arg2 = Class.forName(nms + ".PacketPlayOutCustomPayload").getConstructor(MinecraftKey, PacketDataSerializer);
			Class<?> CraftPlayer = Class.forName("org.bukkit.craftbukkit.v" + version + ".entity.CraftPlayer");
			arg3 = CraftPlayer.getMethod("getHandle");
			Class<?> EntityPlayer = Class.forName(nms + ".EntityPlayer");
			arg4 = EntityPlayer.getDeclaredField("playerConnection");
			arg4.setAccessible(true);
			Class<?> PlayerConnection = Class.forName(nms + ".PlayerConnection");//PlayerConnection
			arg5 = PlayerConnection.getMethod("sendPacket", Class.forName(nms + ".Packet"));
		}catch(Exception e){
			e.printStackTrace();
		}
		newMinecraftKey = arg0;
		newPacketDataSerializer = arg1;
		newPacketPlayOutCustomPayload = arg2;
		getHandle = arg3;
		playerConnection = arg4;
		sendPacket = arg5;
	}

	@Override
	public void onEnable(){
		plugin = this;

		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable(){
		HandlerList.unregisterAll((JavaPlugin) this);
	}

	public static Lectern getPlugin(){
		return plugin;
	}

	@EventHandler(ignoreCancelled = true)
	public void onChange(SignChangeEvent e){
		Player player = e.getPlayer();
		if(player.hasPermission("lectern.create"))
			return;

		if(!SIGN_TITLE.equals(e.getLine(0)))
			return;

		e.setCancelled(true);
		sendMessage(player, ChatColor.RED + (isJapanese(player) ? "あなたには書見台を作成する権限がありません。" : "You don't have permission to create lectern."));
	}

	@EventHandler(ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent e){
		if(e.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;

		Block block = e.getClickedBlock();
		Material material = block.getType();
		if(material != Material.ENCHANTING_TABLE)
			return;

		Block down = block.getRelative(BlockFace.DOWN);
		if(down == null || down.getType() != Material.CHEST)
			return;

		Chest chest = (Chest) down.getState();
		boolean find = false;
		for(BlockFace face : BlockFace.values()){
			Block relative = block.getRelative(face);
			if(relative == null || relative.getType() != Material.WALL_SIGN)
				continue;

			Sign sign = (Sign) relative.getState();
			if(face != ((org.bukkit.material.Sign) sign.getData()).getFacing())
				continue;

			if(!SIGN_TITLE.equals(sign.getLine(0)))
				continue;

			find = true;
			break;
		}

		if(!find)
			return;

		Player player = e.getPlayer();
		if(!player.hasPermission("lectern.use")){
			e.setCancelled(true);
			sendMessage(player, ChatColor.RED + (isJapanese(player) ? "あなたには書見台を使用する権限がありません。" : "You don't have permission to use lectern."));
			return;
		}

		Inventory inventory = Bukkit.createInventory(null, 27, "§8Lectern");
		for(ItemStack item : chest.getBlockInventory().getContents())
			if(item != null && item.getType() == Material.WRITTEN_BOOK)
				inventory.setItem(inventory.firstEmpty(), item);

		while(inventory.firstEmpty() != -1)
			inventory.setItem(inventory.firstEmpty(), glass);

		e.setCancelled(true);
		player.openInventory(inventory);
	}

	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent e){
		Inventory inventory = e.getClickedInventory();
		if(inventory == null)
			return;

		String title = inventory.getTitle();
		if(title == null || !title.equals("§8Lectern"))
			return;

		e.setCancelled(true);

		ItemStack item = e.getCurrentItem();
		if(item == null || item.getType() != Material.WRITTEN_BOOK)
			return;

		if(!(e.getWhoClicked() instanceof Player))
			return;

		Player player = (Player) e.getWhoClicked();
		PlayerInventory playerInventory = player.getInventory();
		ItemStack main = playerInventory.getItemInMainHand();
		playerInventory.setItemInMainHand(item);
		ByteBuf buffer = Unpooled.buffer(256);
		buffer.setByte(0, (byte) 0);
		buffer.writerIndex(1);
		try{
			Object data = newPacketDataSerializer.newInstance(buffer);
			Object key = newMinecraftKey.newInstance("minecraft:book_open");
			Object packet = newPacketPlayOutCustomPayload.newInstance(key, data);
			Object entity = getHandle.invoke(player);
			Object connection = playerConnection.get(entity);
			sendPacket.invoke(connection, packet);
		}catch(Exception ex){
			ex.printStackTrace();
		}finally{
			playerInventory.setItemInMainHand(main);
		}
	}

	public boolean isJapanese(Player player){
		return player.getLocale().equals("ja_jp");
	}

	public void sendMessage(Player player, String message){
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
	}

}
