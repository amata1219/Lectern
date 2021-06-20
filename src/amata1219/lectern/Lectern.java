package amata1219.lectern;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;

import com.google.common.collect.Sets;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
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

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class Lectern extends JavaPlugin implements Listener {

	private static final String SIGN_TITLE = "[Lectern]";
	private static final HashSet<Material> BOOK_STORAGE_TYPES = Sets.newHashSet(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);
	private static final ItemStack BACKGROUND = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
	private static final Object packetPlayOutOpenBook;
	private static final Method getHandle;
	private static final Field playerConnection;
	private static final Method sendPacket;

	static{
		String version = Bukkit.getServer().getClass().getPackage().getName().replaceFirst(".*(\\d+_\\d+_R\\d+).*", "$1");
		String nms = "net.minecraft.server.v" + version;
		Object arg0 = null;
		Method arg1 = null;
		Field arg2 = null;
		Method arg3 = null;
		try{
			Class<?> EnumHand = Class.forName(nms + ".EnumHand");
			Object MAIN_HAND = null;
			for(Object value : EnumHand.getEnumConstants()){
				if(value.toString().equals("MAIN_HAND")){
					MAIN_HAND = value;
					break;
				}
			}
			
			Class<?> PacketPlayOutOpenBook = Class.forName(nms + ".PacketPlayOutOpenBook");
			Constructor<?> constructor = PacketPlayOutOpenBook.getConstructor(EnumHand);
			
			arg0 = constructor.newInstance(MAIN_HAND);
			
			Class<?> CraftPlayer = Class.forName("org.bukkit.craftbukkit.v" + version + ".entity.CraftPlayer");
			arg1 = CraftPlayer.getMethod("getHandle");
			Class<?> EntityPlayer = Class.forName(nms + ".EntityPlayer");
			arg2 = EntityPlayer.getDeclaredField("playerConnection");
			arg2.setAccessible(true);
			Class<?> PlayerConnection = Class.forName(nms + ".PlayerConnection");
			arg3 = PlayerConnection.getMethod("sendPacket", Class.forName(nms + ".Packet"));
		}catch(Exception e){
			e.printStackTrace();
		}
		
		packetPlayOutOpenBook = arg0;
		getHandle = arg1;
		playerConnection = arg2;
		sendPacket = arg3;
	}

	@Override
	public void onEnable(){
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable(){
		HandlerList.unregisterAll((JavaPlugin) this);
	}

	@EventHandler(ignoreCancelled = true)
	public void onChange(SignChangeEvent e){
		Player player = e.getPlayer();
		if(player.hasPermission("lectern.create"))
			return;

		if(!SIGN_TITLE.equals(ChatColor.stripColor(e.getLine(0)))) return;

		e.setCancelled(true);
		sendMessage(player, ChatColor.RED + (isJapanese(player) ? "あなたには書見台を作成する権限がありません。" : "You don't have permission to create lectern."));
	}

	@EventHandler(ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent e){
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Block block = e.getClickedBlock();
		Material material = block.getType();

		Block bookStorage = null;
		if (material == Material.ENCHANTING_TABLE) {
			Block down = block.getRelative(BlockFace.DOWN);
			if (isNotBookStorageType(down.getType())) return;

			boolean find = false;
			for(BlockFace face : BlockFace.values()){
				Block relative = block.getRelative(face);
				if(!relative.getType().toString().endsWith("_WALL_SIGN")) continue;

				if(face != ((Directional) relative.getBlockData()).getFacing()) continue;

				Sign sign = (Sign) relative.getState();
				if(isLecternSign(sign)){
					find = true;
					break;
				}
			}

			if(!find) return;

			bookStorage = down;
		} else if (material.toString().endsWith("_WALL_SIGN")) {
			BlockFace signFace = ((Directional) block.getBlockData()).getFacing();
			Block blockFaced = block.getRelative(signFace.getOppositeFace());
			if (block.getType() != Material.ENCHANTING_TABLE) return;

			Sign sign = (Sign) block.getState();
			if (!isLecternSign(sign)) return;

			Block down = blockFaced.getRelative(BlockFace.DOWN);
			if(isNotBookStorageType(down.getType())) return;

			bookStorage = down;
		} else {
			return;
		}

		Player player = e.getPlayer();
		if(!player.hasPermission("lectern.use")){
			e.setCancelled(true);
			sendMessage(player, ChatColor.RED + (isJapanese(player) ? "あなたには書見台を使用する権限がありません。" : "You don't have permission to use lectern."));
			return;
		}

		Inventory inventory = Bukkit.createInventory(null, 27, "§8Lectern");
		for(ItemStack item : ((Container) bookStorage.getState()).getInventory().getContents())
			if(item != null && item.getType() == Material.WRITTEN_BOOK)
				inventory.setItem(inventory.firstEmpty(), item);

		while(inventory.firstEmpty() != -1)
			inventory.setItem(inventory.firstEmpty(), BACKGROUND);

		e.setCancelled(true);
		player.openInventory(inventory);
	}

	private static boolean isLecternSign(Sign sign) {
		return SIGN_TITLE.equals(ChatColor.stripColor(sign.getLine(0)));
	}

	private static boolean isNotBookStorageType(Material type) {
		return !BOOK_STORAGE_TYPES.contains(type);
	}

	@EventHandler(ignoreCancelled = true)
	public void onClick(InventoryClickEvent e){
		String title = e.getView().getTitle();
		if(!title.equals("§8Lectern")) return;

		e.setCancelled(true);

		ItemStack item = e.getCurrentItem();
		if(item == null || item.getType() != Material.WRITTEN_BOOK) return;

		if(!(e.getWhoClicked() instanceof Player)) return;

		Player player = (Player) e.getWhoClicked();
		PlayerInventory inventory = player.getInventory();
		ItemStack main = inventory.getItemInMainHand();
		
		try{
			inventory.setItemInMainHand(item);
			Object entity = getHandle.invoke(player);
			Object connection = playerConnection.get(entity);
			sendPacket.invoke(connection, packetPlayOutOpenBook);
		}catch(Exception ex){
			ex.printStackTrace();
		}finally{
			inventory.setItemInMainHand(main);
		}
		/*ByteBuf buffer = Unpooled.buffer(256);
		buffer.setByte(0, (byte) 0);
		buffer.writerIndex(1);
		
		try{
			Object data = newPacketDataSerializer.newInstance(buffer);
			Object key = newMinecraftKey.newInstance("minecraft:book_open");
			Object packet = newPacketPlayOutCustomPayload.newInstance(key, data);
			Object entity = getHandle.invoke(player);
			Object connection = playerConnection.get(entity);
			sendPacket.invoke(connection, packet);
			System.out.println("sent");
		}catch(Exception ex){
			ex.printStackTrace();
		}finally{
			playerInventory.setItemInMainHand(main);
		}*/
	}

	public boolean isJapanese(Player player){
		return player.getLocale().equals("ja_jp");
	}

	public void sendMessage(Player player, String message){
		player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
	}

}
