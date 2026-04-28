const sharp = require('sharp');
const path = require('path');

const input = process.argv[2];
const output = process.argv[3];
const threshold = 35;

async function removeBg() {
    const image = sharp(input).ensureAlpha();
    const { data, info } = await image.raw().toBuffer({ resolveWithObject: true });
    const { width, height, channels } = info;

    for (let i = 0; i < data.length; i += channels) {
        const r = data[i], g = data[i + 1], b = data[i + 2];
        if (r < threshold && g < threshold && b < threshold) {
            data[i + 3] = 0;
        }
    }

    await sharp(data, { raw: { width, height, channels } })
        .png()
        .toFile(output);

    console.log('Background removed: ' + output);
}

removeBg().catch(console.error);
