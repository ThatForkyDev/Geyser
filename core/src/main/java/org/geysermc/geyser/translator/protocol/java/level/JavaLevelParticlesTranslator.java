/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.protocol.java.level;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.ItemStack;
import com.github.steveice10.mc.protocol.data.game.level.particle.*;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.data.LevelEventType;
import com.nukkitx.protocol.bedrock.data.inventory.ItemData;
import com.nukkitx.protocol.bedrock.packet.LevelEventPacket;
import com.nukkitx.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.inventory.item.ItemTranslator;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ParticleMapping;
import org.geysermc.geyser.util.DimensionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.Arrays;

@Translator(packet = ClientboundLevelParticlesPacket.class)
public class JavaLevelParticlesTranslator extends PacketTranslator<ClientboundLevelParticlesPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundLevelParticlesPacket packet) {
        boolean optionalPackAssured = GeyserImpl.getInstance().getConfig().isEnableOptionalPackRequiredFeatures();
        Function<Vector3f, BedrockPacket> optionalPackParticleCreateFunction = createOptionalPackParticle(
            session, packet.getParticle(), packet.getAmount(), packet.getOffsetX(), packet.getOffsetY(), packet.getOffsetZ(), packet.getVelocityOffset()
        );
        Function<Vector3f, BedrockPacket> particleCreateFunction = createParticle(session, packet.getParticle());
        if (optionalPackAssured && optionalPackParticleCreateFunction != null) {
            // Spawn particle using special variables in OptionalPack for correct count, volume, velocity, etc.
            Vector3f position = Vector3f.from(packet.getX(), packet.getY(), packet.getZ());
            session.sendUpstreamPacket(optionalPackParticleCreateFunction.apply(position));
        } else if (particleCreateFunction != null) {
            if (packet.getAmount() == 0) {
                // 0 means don't apply the offset
                Vector3f position = Vector3f.from(packet.getX(), packet.getY(), packet.getZ());
                session.sendUpstreamPacket(particleCreateFunction.apply(position));
            } else {
                Random random = ThreadLocalRandom.current();
                for (int i = 0; i < packet.getAmount(); i++) {
                    double offsetX = random.nextGaussian() * (double) packet.getOffsetX();
                    double offsetY = random.nextGaussian() * (double) packet.getOffsetY();
                    double offsetZ = random.nextGaussian() * (double) packet.getOffsetZ();
                    Vector3f position = Vector3f.from(packet.getX() + offsetX, packet.getY() + offsetY, packet.getZ() + offsetZ);
                    session.sendUpstreamPacket(particleCreateFunction.apply(position));
                }
            }
        } else {
            // Null is only returned when no particle of this type is found
            session.getGeyser().getLogger().debug("Unhandled particle packet: " + packet);
        }
    }

    /**
     * @param molangVariable the name of the molang variable.
     * @param value the float value of the molang variable.
     * @return a method to create a member array of molang sub-variables to be inserted into molangVariablesJson
     */

    private static ObjectNode createMolangVariablesObject(String molangVariable, float value) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode particleValue = mapper.createObjectNode();
        ObjectNode particleValueValue = mapper.createObjectNode();
        particleValue.put("name", molangVariable);
        particleValueValue.put("type", "float");
        particleValueValue.put("value", value);
        particleValue.set("value", particleValueValue);
        return particleValue;
    }

    /**
     * @param molangVariable the name of the molang variable.
     * @param nodes an array of ObjectNodes to use as sub-variables.
     * @return a method to create a member array of molang sub-variables to be inserted into molangVariablesJson.
     */

    private static ObjectNode createMolangVariablesMemberArrayObject(String molangVariable, ObjectNode[] nodes) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode particleMember = mapper.createObjectNode();
        ObjectNode particleMemberValue = mapper.createObjectNode();
        particleMember.put("name", molangVariable);
        particleMemberValue.put("type", "member_array");
        ArrayNode particleMemberValueArray = mapper.createArrayNode();
        particleMemberValueArray.addAll(Arrays.asList(nodes));
        particleMemberValue.set("value", particleMemberValueArray);
        particleMember.set("value", particleMemberValue);
        return particleMember;
    }

    /**
     * @param count the amount of particles to create.
     * @param offsetX the X offset to apply to the particle volume.
     * @param offsetY the Y offset to apply to the particle volume.
     * @param offsetZ the Z offset to apply to the particle volume.
     * @param velocityOffset the velocity offset to apply to the particle.
     * @return a method to create an array of standard particle variables to be inserted into molangVariablesJson
     */

    private static ArrayNode createDefaultMolangVariablesArray(int count, float offsetX, float offsetY, float offsetZ, float velocityOffset) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode particleCount = createMolangVariablesObject("variable.amount", (float) count);
        ObjectNode particleSpeed = createMolangVariablesObject("variable.velocity", velocityOffset);
        ObjectNode particleOffsetX = createMolangVariablesObject(".x", offsetX);
        ObjectNode particleOffsetY = createMolangVariablesObject(".y", offsetY);
        ObjectNode particleOffsetZ = createMolangVariablesObject(".z", offsetZ);
        ObjectNode particleOffset = createMolangVariablesMemberArrayObject("variable.offset", new ObjectNode[]{particleOffsetX, particleOffsetY, particleOffsetZ});
        ArrayNode molangVariableArray = mapper.createArrayNode();
        molangVariableArray.addAll(Arrays.asList(particleCount, particleSpeed, particleOffset));
        return molangVariableArray;
    }

    /**
     * @param session the Bedrock client session.
     * @param particle the Java particle to translate to a Bedrock equivalent.
     * @param count the amount of particles to create.
     * @param offsetX the X offset to apply to the particle volume.
     * @param offsetY the Y offset to apply to the particle volume.
     * @param offsetZ the Z offset to apply to the particle volume.
     * @param velocityOffset the velocity offset to apply to the particle.
     * @return a function to create a packet with a specified particle, in the event we need to spawn multiple particles
     * with different offsets.
     */
    private Function<Vector3f, BedrockPacket> createOptionalPackParticle(GeyserSession session, Particle particle, int count, float offsetX, float offsetY, float offsetZ, float velocityOffset) {
        ParticleMapping particleMapping = Registries.PARTICLES.get(particle.getType());
        switch (particle.getType()) {
            case BLOCK, FALLING_DUST, ITEM -> {
                // These particles must be handled by level event due to inclusion of item/block data
                return null;
            }
            case BLOCK_MARKER -> { //TODO
                // Use level event for unused particle with terrain texture atlas
                return null;
            }
            case DUST -> { //TODO
                // Use molangVariablesJSON for color values
                // Red Float 0-1
                // Green Float 0-1
                // Blue Float 0-1
                // Scale Float 0.01-4
                return null;
            }
            case DUST_COLOR_TRANSITION -> { //TODO
                // Use molangVariablesJSON for color values
                // FromRed Float 0-1
                // FromGreen Float 0-1
                // FromBlue Float 0-1
                // Scale Float 0.01-4
                // Red Float 0-1
                // Green Float 0-1
                // Blue Float 0-1
                return null;
            }
            case VIBRATION -> { //TODO
                // No idea how this works
                // Origin Position 'Starting position'
                // PositionType String 'Type of destination'
                // BlockPosition Position 'Present if PositionType is "minecraft:block"'
                // EntityId Varint 'Present if PositionType is "minecraft:entity"'
                // Ticks Varint
                return null;
            }
            default -> {
                return (position) -> {
                    int dimensionId = DimensionUtils.javaToBedrock(session.getDimension());
                    SpawnParticleEffectPacket stringPacket = new SpawnParticleEffectPacket();
                    stringPacket.setIdentifier(particleMapping.identifier());
                    stringPacket.setDimensionId(dimensionId);
                    stringPacket.setPosition(position);
                    ArrayNode defaultMolangVariableArray = createDefaultMolangVariablesArray(count, offsetX, offsetY, offsetZ, velocityOffset);
                    session.getGeyser().getLogger().debug("Molang Particle Variables:" + defaultMolangVariableArray.toString());
                    stringPacket.setMolangVariablesJson(Optional.ofNullable(defaultMolangVariableArray.toString()));
                    return stringPacket;
                };
            }
        }
    }
    /**
     * @param session the Bedrock client session.
     * @param particle the Java particle to translate to a Bedrock equivalent.
     * @return a function to create a packet with a specified particle, in the event we need to spawn multiple particles
     * with different offsets.
     */
    private Function<Vector3f, BedrockPacket> createParticle(GeyserSession session, Particle particle) {
        switch (particle.getType()) {
            case BLOCK -> {
                int blockState = session.getBlockMappings().getBedrockBlockId(((BlockParticleData) particle.getData()).getBlockState());
                return (position) -> {
                    LevelEventPacket packet = new LevelEventPacket();
                    packet.setType(LevelEventType.PARTICLE_CRACK_BLOCK);
                    packet.setPosition(position);
                    packet.setData(blockState);
                    return packet;
                };
            }
            case FALLING_DUST -> {
                int blockState = session.getBlockMappings().getBedrockBlockId(((FallingDustParticleData) particle.getData()).getBlockState());
                return (position) -> {
                    LevelEventPacket packet = new LevelEventPacket();
                    // In fact, FallingDustParticle should have data like DustParticle,
                    // but in MCProtocol, its data is BlockState(1).
                    packet.setType(LevelEventType.PARTICLE_FALLING_DUST);
                    packet.setData(blockState);
                    packet.setPosition(position);
                    return packet;
                };
            }
            case ITEM -> {
                ItemStack javaItem = ((ItemParticleData) particle.getData()).getItemStack();
                ItemData bedrockItem = ItemTranslator.translateToBedrock(session, javaItem);
                int data = bedrockItem.getId() << 16 | bedrockItem.getDamage();
                return (position) -> {
                    LevelEventPacket packet = new LevelEventPacket();
                    packet.setType(LevelEventType.PARTICLE_ITEM_BREAK);
                    packet.setData(data);
                    packet.setPosition(position);
                    return packet;
                };
            }
            case DUST, DUST_COLOR_TRANSITION -> { //TODO
                DustParticleData data = (DustParticleData) particle.getData();
                int r = (int) (data.getRed() * 255);
                int g = (int) (data.getGreen() * 255);
                int b = (int) (data.getBlue() * 255);
                int rgbData = ((0xff) << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                return (position) -> {
                    LevelEventPacket packet = new LevelEventPacket();
                    packet.setType(LevelEventType.PARTICLE_FALLING_DUST);
                    packet.setData(rgbData);
                    packet.setPosition(position);
                    return packet;
                };
            }
            default -> {
                ParticleMapping particleMapping = Registries.PARTICLES.get(particle.getType());
                if (particleMapping == null) { //TODO ensure no particle can be null
                    return null;
                }

                if (particleMapping.levelEventType() != null) {
                    return (position) -> {
                        LevelEventPacket packet = new LevelEventPacket();
                        packet.setType(particleMapping.levelEventType());
                        packet.setPosition(position);
                        return packet;
                    };
                } else if (particleMapping.identifier() != null) {
                    int dimensionId = DimensionUtils.javaToBedrock(session.getDimension());
                    return (position) -> {
                        SpawnParticleEffectPacket stringPacket = new SpawnParticleEffectPacket();
                        stringPacket.setIdentifier(particleMapping.identifier());
                        stringPacket.setDimensionId(dimensionId);
                        stringPacket.setPosition(position);
                        stringPacket.setMolangVariablesJson(Optional.empty());
                        return stringPacket;
                    };
                } else {
                    return null;
                }
            }
        }
    }
}