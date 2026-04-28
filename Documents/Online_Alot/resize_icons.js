const sharp = require('sharp');
const path = require('path');

const input = 'c:\\Users\\asus\\Documents\\Online_Alot\\logo_nobg.png';
const base = 'c:\\Users\\asus\\Documents\\Online_Alot\\app\\src\\main\\res';

const sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
};

async function resize() {
    for (const [folder, size] of Object.entries(sizes)) {
        const dir = path.join(base, folder);
        await sharp(input).resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toFile(path.join(dir, 'ic_launcher.png'));
        await sharp(input).resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toFile(path.join(dir, 'ic_launcher_round.png'));
        console.log(`${folder}: ${size}x${size} done`);
    }

    // In-app logo drawable
    await sharp(input).resize(512, 512, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toFile(path.join(base, 'drawable', 'ic_barbershop_logo.png'));
    console.log('drawable logo done');

    // Foreground for adaptive icon
    await sharp(input).resize(432, 432, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toFile(path.join(base, 'drawable', 'ic_launcher_foreground_img.png'));
    console.log('adaptive foreground done');
}

resize().catch(console.error);
