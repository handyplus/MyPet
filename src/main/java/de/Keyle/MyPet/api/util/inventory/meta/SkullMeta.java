/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.api.util.inventory.meta;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.Keyle.MyPet.util.nbt.TagCompound;
import de.Keyle.MyPet.util.nbt.TagList;
import de.Keyle.MyPet.util.nbt.TagString;

public class SkullMeta implements IconMeta {
    String name = null;
    String texture = null;

    /**
     * 获取该头颅的所有者。
     *
     * @return 该头颅的所有者
     */
    public String getOwner() {
        return name;
    }

    /**
     * 检查该头颅是否设置了所有者。
     *
     * @return 如果该头颅有所有者则返回 true
     */
    public boolean hasOwner() {
        return name != null;
    }

    /**
     * 设置该头颅的所有者。
     * <p/>
     * 插件在调用此方法前应先检查 hasOwner() 是否返回 true。
     *
     * @param name 该头颅的新所有者
     * @return 如果所有者设置成功则返回 true
     */
    public boolean setOwner(String name) {
        if (name != null && name.length() > 16) {
            return false;
        } else {
            this.name = name;
            return true;
        }
    }

    /**
     * 获取该头颅的材质 URL。
     *
     * @return 该头颅的材质 URL
     */
    public String getTexture() {
        return texture;
    }

    /**
     * 检查该头颅是否设置了材质 URL。
     *
     * @return 如果该头颅有材质 URL 则返回 true
     */
    public boolean hasTexture() {
        return texture != null;
    }

    /**
     * 设置该头颅的材质 URL。
     * <p/>
     * 插件在调用此方法前应先检查 hasTexture() 是否返回 true。
     *
     * @param url 该头颅材质的 URL
     * @return 如果 URL 设置成功则返回 true
     */
    public boolean setTexture(String url) {
        if (hasOwner()) {
            this.texture = url;
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public void applyTo(TagCompound tag) {
        if (hasOwner()) {
            TagCompound ownerTag = new TagCompound();


            ownerTag.put("Name", new TagString(getOwner()));

            if (hasTexture()) {
                TagCompound propertiesTag = new TagCompound();
                TagList textureList = new TagList();
                TagCompound textureTag = new TagCompound();
                JsonObject jsonObject = new JsonObject();
                JsonObject texturesObject = new JsonObject();
                JsonObject skinObject = new JsonObject();
                jsonObject.add("textures", texturesObject);
                texturesObject.add("SKIN", skinObject);
                skinObject.addProperty("url", getTexture());
                String base64 = BaseEncoding.base64Url().encode(new Gson().toJson(jsonObject).getBytes());
                textureTag.put("Value", new TagString(base64));
                textureList.addTag(textureTag);
                propertiesTag.put("textures", textureList);
                ownerTag.put("Properties", propertiesTag);
            }

            tag.put("SkullOwner", ownerTag);
        }
    }

    public SkullMeta clone() {
        SkullMeta meta = new SkullMeta();
        meta.name = this.name;
        meta.texture = this.texture;
        return meta;
    }
}
