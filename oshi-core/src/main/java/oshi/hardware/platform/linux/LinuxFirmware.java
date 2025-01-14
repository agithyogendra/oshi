/**
 * OSHI (https://github.com/oshi/oshi)
 *
 * Copyright (c) 2010 - 2019 The OSHI Project Team:
 * https://github.com/oshi/oshi/graphs/contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package oshi.hardware.platform.linux;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import oshi.hardware.common.AbstractFirmware;
import oshi.util.Constants;
import oshi.util.ExecutingCommand;
import oshi.util.FileUtil;
import oshi.util.ParseUtil;

/**
 * Firmware data obtained by sysfs.
 */
final class LinuxFirmware extends AbstractFirmware {

    private static final long serialVersionUID = 1L;

    // Jan 13 2013 16:24:29
    private static final DateTimeFormatter VCGEN_FORMATTER = DateTimeFormatter.ofPattern("MMM d uuuu HH:mm:ss",
            Locale.ENGLISH);

    /** {@inheritDoc} */
    @Override
    public String getManufacturer() {
        if (this.manufacturer == null && !queryManufacturerFromSysfs() && !queryVcGenCmd()) {
            this.manufacturer = Constants.UNKNOWN;
        }
        return super.getManufacturer();
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        if (this.description == null && !queryDescriptionFromSysfs() && !queryVcGenCmd()) {
            this.description = Constants.UNKNOWN;
        }
        return this.description;
    }

    /** {@inheritDoc} */
    @Override
    public String getVersion() {
        if (this.version == null && !queryVersionFromSysfs() && !queryVcGenCmd()) {
            this.version = Constants.UNKNOWN;
        }
        return this.version;
    }

    /** {@inheritDoc} */
    @Override
    public String getReleaseDate() {
        if (this.releaseDate == null && !queryReleaseDateFromSysfs() && !queryVcGenCmd()) {
            this.releaseDate = Constants.UNKNOWN;
        }
        return this.releaseDate;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        if (this.name == null && !queryVcGenCmd()) {
            this.name = "BIOS";
        }
        return this.name;
    }

    // $ ls /sys/devices/virtual/dmi/id/
    // bios_date board_vendor chassis_version product_version
    // bios_vendor board_version modalias subsystem
    // bios_version chassis_asset_tag power sys_vendor
    // board_asset_tag chassis_serial product_name uevent
    // board_name chassis_type product_serial
    // board_serial chassis_vendor product_uuid

    private boolean queryManufacturerFromSysfs() {
        final String biosVendor = FileUtil.getStringFromFile(Constants.SYSFS_SERIAL_PATH + "bios_vendor").trim();
        if (!biosVendor.isEmpty()) {
            this.manufacturer = biosVendor;
            return true;
        }
        return false;
    }

    private boolean queryDescriptionFromSysfs() {
        final String modalias = FileUtil.getStringFromFile(Constants.SYSFS_SERIAL_PATH + "modalias").trim();
        if (!modalias.isEmpty()) {
            this.description = modalias;
            return true;
        }
        return false;
    }

    private boolean queryVersionFromSysfs() {
        final String biosVersion = FileUtil.getStringFromFile(Constants.SYSFS_SERIAL_PATH + "bios_version").trim();
        if (!biosVersion.isEmpty()) {
            String biosRevision = queryBiosRevision();
            this.version = biosVersion + (biosRevision.isEmpty() ? "" : " (revision " + biosRevision + ")");
            return true;
        }
        return false;
    }

    private boolean queryReleaseDateFromSysfs() {
        final String biosDate = FileUtil.getStringFromFile(Constants.SYSFS_SERIAL_PATH + "bios_date").trim();
        if (!biosDate.isEmpty()) {
            this.releaseDate = ParseUtil.parseMmDdYyyyToYyyyMmDD(biosDate);
            return true;
        }
        return false;
    }

    // $ sudo dmidecode -t bios
    // # dmidecode 2.11
    // SMBIOS 2.4 present.
    //
    // Handle 0x0000, DMI type 0, 24 bytes
    // BIOS Information
    // Vendor: Phoenix Technologies LTD
    // Version: 6.00
    // Release Date: 07/02/2015
    // Address: 0xEA5E0
    // Runtime Size: 88608 bytes
    // ROM Size: 64 kB
    // Characteristics:
    // ISA is supported
    // PCI is supported
    // PC Card (PCMCIA) is supported
    // PNP is supported
    // APM is supported
    // BIOS is upgradeable
    // BIOS shadowing is allowed
    // ESCD support is available
    // Boot from CD is supported
    // Selectable boot is supported
    // EDD is supported
    // Print screen service is supported (int 5h)
    // 8042 keyboard services are supported (int 9h)
    // Serial services are supported (int 14h)
    // Printer services are supported (int 17h)
    // CGA/mono video services are supported (int 10h)
    // ACPI is supported
    // Smart battery is supported
    // BIOS boot specification is supported
    // Function key-initiated network boot is supported
    // Targeted content distribution is supported
    // BIOS Revision: 4.6
    // Firmware Revision: 0.0

    private String queryBiosRevision() {
        final String marker = "Bios Revision:";
        // Requires root, may not return anything
        for (final String checkLine : ExecutingCommand.runNative("dmidecode -t bios")) {
            if (checkLine.contains(marker)) {
                return checkLine.split(marker)[1].trim();
            }
        }
        return "";
    }

    private boolean queryVcGenCmd() {
        List<String> vcgencmd = ExecutingCommand.runNative("vcgencmd version");
        if (vcgencmd.size() >= 3) {
            // First line is date
            try {
                this.releaseDate = DateTimeFormatter.ISO_LOCAL_DATE.format(VCGEN_FORMATTER.parse(vcgencmd.get(0)));
            } catch (DateTimeParseException e) {
                this.releaseDate = Constants.UNKNOWN;
            }
            // Second line is copyright
            String[] copyright = ParseUtil.whitespaces.split(vcgencmd.get(1));
            this.manufacturer = copyright[copyright.length - 1];
            // Third line is version
            this.version = vcgencmd.get(2).replace("version ", "");
            this.name = "RPi";
            this.description = "Bootloader";
            return true;
        }
        return false;
    }

}
