package zone.moddev.mc.ballpark;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import zone.moddev.mc.ballpark.command.BallparkCommands;

/**
 * Ballpark gives ProjectE estimated EMC values for modded items, from datapack rules.
 */
@Mod(Ballpark.MOD_ID)
public final class Ballpark {
    public static final String MOD_ID = "ballpark";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ballpark() {
        NeoForge.EVENT_BUS.addListener(BallparkCommands::register);
    }
}
