package zone.moddev.mc.emcballpark;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import zone.moddev.mc.emcballpark.command.BallparkCommands;

/**
 * EMC Ballpark gives ProjectE estimated EMC values for modded items, from datapack rules.
 */
@Mod(EMCBallpark.MOD_ID)
public final class EMCBallpark {
    public static final String MOD_ID = "emcballpark";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EMCBallpark() {
        NeoForge.EVENT_BUS.addListener(BallparkCommands::register);
    }
}
