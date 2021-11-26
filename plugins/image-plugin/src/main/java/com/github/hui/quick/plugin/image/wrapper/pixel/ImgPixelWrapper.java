package com.github.hui.quick.plugin.image.wrapper.pixel;

import com.github.hui.quick.plugin.base.FileReadUtil;
import com.github.hui.quick.plugin.base.FileWriteUtil;
import com.github.hui.quick.plugin.base.GraphicUtil;
import com.github.hui.quick.plugin.base.IoUtil;
import com.github.hui.quick.plugin.base.constants.MediaType;
import com.github.hui.quick.plugin.base.gif.GifDecoder;
import com.github.hui.quick.plugin.base.gif.GifHelper;
import com.github.hui.quick.plugin.image.wrapper.pixel.context.PixelContextHolder;
import com.github.hui.quick.plugin.image.wrapper.pixel.model.IPixelStyle;
import com.github.hui.quick.plugin.image.wrapper.pixel.model.PixelStyleEnum;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.github.hui.quick.plugin.image.util.PredicateUtil.conditionGetOrElse;

/**
 * @author yihui
 * @date 21/11/8
 */
public class ImgPixelWrapper {
    private static Logger log = LoggerFactory.getLogger(ImgPixelWrapper.class);

    private ImgPixelOptions pixelOptions;

    static {
        ImageIO.scanForPlugins();
    }

    public static Builder build() {
        return new Builder();
    }

    private ImgPixelWrapper(ImgPixelOptions pixelOptions) {
        this.pixelOptions = pixelOptions;
    }

    /**
     * 转成像素图片
     *
     * @return
     */
    public BufferedImage asBufferedImg() {
        if (pixelOptions.getSource() == null) {
            throw new IllegalArgumentException("bufferedImage cannot be null.");
        }

        return parseImg(pixelOptions.getSource()).left;
    }

    /**
     * 转gif
     *
     * @return
     */
    public ByteArrayOutputStream asGif() {
        GifDecoder decoder = pixelOptions.getGifSource();
        if (decoder == null) {
            throw new IllegalArgumentException("gif source img cannot be null.");
        }

        int gifCnt = decoder.getFrameCount();
        List<ImmutablePair<BufferedImage, Integer>> renderImgList = new ArrayList<>(gifCnt);
        for (int index = 0; index < gifCnt; index++) {
            ImmutablePair<BufferedImage, PixelContextHolder.ImgPixelChar> pair = parseImg(decoder.getFrame(index));
            renderImgList.add(ImmutablePair.of(pair.left, decoder.getDelay(index)));
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GifHelper.saveGif(renderImgList, outputStream);
        return outputStream;
    }

    /**
     * 保存到文件
     *
     * @param fileName
     * @return
     * @throws Exception
     */
    public boolean asFile(String fileName) throws Exception {
        File file = new File(fileName);
        FileWriteUtil.mkDir(file.getParentFile());

        if (pixelOptions.getGifSource() != null) {
            try (ByteArrayOutputStream output = asGif()) {
                FileOutputStream out = new FileOutputStream(file);
                out.write(output.toByteArray());
                out.flush();
                out.close();
            }
            return true;
        }

        BufferedImage bufferedImage = asBufferedImg();
        if (!ImageIO.write(bufferedImage, pixelOptions.getPicType(), file)) {
            throw new IOException("save pixel render image to: " + fileName + " error!");
        }

        return true;
    }

    /**
     * 图片转字符时，输出字符数组
     *
     * @return
     */
    public List<List<String>> asChars() {
        // 静态图的转换
        if (pixelOptions.getSource() != null) {
            return PixelContextHolder.toPixelChars(parseImg(pixelOptions.getSource()).getRight());
        }

        // gif图的转换
        GifDecoder decoder = pixelOptions.getGifSource();
        int gifCnt = decoder.getFrameCount();
        PixelContextHolder.ImgPixelChar imgPixelChar = null;
        for (int index = 0; index < gifCnt; index++) {
            ImmutablePair<BufferedImage, PixelContextHolder.ImgPixelChar> pair = parseImg(decoder.getFrame(index));
            pair.right.setPre(imgPixelChar);
            imgPixelChar = pair.right;
        }

        return PixelContextHolder.toPixelChars(imgPixelChar);
    }

    private ImmutablePair<BufferedImage, PixelContextHolder.ImgPixelChar> parseImg(BufferedImage source) {
        try {
            PixelContextHolder.newPic();
            if (pixelOptions.getRate() != 1) {
                source = GraphicUtil.scaleImg((int) (source.getWidth() * pixelOptions.getRate()), (int) (source.getHeight() * pixelOptions.getRate()), source);
            }
            int picWidth = source.getWidth();
            int picHeight = source.getHeight();
            int blockSize = pixelOptions.getBlockSize();
            IPixelStyle pixelType = pixelOptions.getPixelType();
            BufferedImage pixelImg = new BufferedImage(picWidth / blockSize * blockSize, picHeight / blockSize * blockSize, source.getType());
            Graphics2D g2d = GraphicUtil.getG2d(pixelImg);
            g2d.setColor(null);
            g2d.fillRect(0, 0, picWidth, picHeight);
            for (int y = 0; y < picHeight; y += blockSize) {
                for (int x = 0; x < picWidth; x += blockSize) {
                    pixelType.render(g2d, source, pixelOptions, x, y);
                }
            }
            g2d.dispose();
            return ImmutablePair.of(pixelImg, PixelContextHolder.getPixelChar());
        } finally {
            PixelContextHolder.clear();
        }
    }

    public static class Builder {
        private static final String DEFAULT_CHAR_SET = "$@B%8&WM#*oahkbdpqwmZO0QLCJUYXzcvunxrjft/\\|()1{}[]?-_+~<>i!lI;:,\\\"^`'. ";
        private static final double DEFAULT_RATE = 1D;
        private static final int DEFAULT_BLOCK_SIZE = 1;
        private static final String DEFAULT_FONT_NAME = "宋体";

        private ImgPixelOptions pixelOptions;

        private String fontName;
        private int fontStyle;

        private Builder() {
            pixelOptions = new ImgPixelOptions();
        }

        /**
         * 设置原始图， 支持gif/jpeg/png
         *
         * @param img
         * @return
         */
        public Builder setSourceImg(String img) {
            try {
                return setSourceImg(FileReadUtil.getStreamByFileName(img));
            } catch (IOException e) {
                throw new RuntimeException("failed to load " + img, e);
            }
        }

        /**
         * 设置原始图， 支持jpeg/png
         *
         * @param img
         * @return
         */
        public Builder setSourceImg(BufferedImage img) {
            pixelOptions.setSource(img);
            return this;
        }

        public Builder setSourceImg(InputStream inputStream) {
            try (ByteArrayInputStream target = IoUtil.toByteArrayInputStream(inputStream)) {
                MediaType media = MediaType.typeOfMagicNum(FileReadUtil.getMagicNum(target));
                if (media != null) {
                    pixelOptions.setPicType(media.getExt());
                }
                if (media == MediaType.ImageGif) {
                    GifDecoder gifDecoder = new GifDecoder();
                    gifDecoder.read(target);
                    pixelOptions.setGifSource(gifDecoder);
                } else {
                    pixelOptions.setSource(ImageIO.read(target));
                }
            } catch (IOException e) {
                log.error("load backgroundImg error!", e);
                throw new RuntimeException("load sourceImg error!", e);
            }
            return this;
        }

        public Builder setBlockSize(int size) {
            pixelOptions.setBlockSize(size);
            return this;
        }

        /**
         * 设置像素模式： 灰度 + 像素化 + 字符化
         *
         * @param pixelType
         * @return
         */
        public Builder setPixelType(IPixelStyle pixelType) {
            pixelOptions.setPixelType(pixelType);
            return this;
        }

        /**
         * 设置字符图片的字符集
         *
         * @param chars
         * @return
         */
        public Builder setChars(String chars) {
            pixelOptions.setChars(chars);
            return this;
        }

        /**
         * 设置缩放比例
         *
         * @param rate
         * @return
         */
        public Builder setRate(Double rate) {
            pixelOptions.setRate(rate);
            return this;
        }


        public Builder setFontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public Builder setFontStyle(int fontStyle) {
            this.fontStyle = fontStyle;
            return this;
        }

        public Builder setPicType(String picType) {
            pixelOptions.setPicType(picType);
            return this;
        }

        public ImgPixelWrapper build() {
            pixelOptions.setBlockSize(conditionGetOrElse((s) -> s > 0, pixelOptions.getBlockSize(), DEFAULT_BLOCK_SIZE));
            pixelOptions.setPixelType(conditionGetOrElse(Objects::nonNull, pixelOptions.getPixelType(), PixelStyleEnum.CHAR_COLOR));
            pixelOptions.setChars(conditionGetOrElse(Objects::nonNull, pixelOptions.getChars(), DEFAULT_CHAR_SET));
            pixelOptions.setRate(conditionGetOrElse(Objects::nonNull, pixelOptions.getRate(), DEFAULT_RATE));
            fontStyle = conditionGetOrElse((s) -> s >= 0 && s <= 3, fontStyle, Font.PLAIN);
            fontName = conditionGetOrElse(Objects::nonNull, fontName, DEFAULT_FONT_NAME);
            pixelOptions.setFont(new Font(fontName, fontStyle, pixelOptions.getBlockSize()));
            pixelOptions.setPicType(conditionGetOrElse(Objects::nonNull, pixelOptions.getPicType(), MediaType.ImageJpg.getExt()));
            return new ImgPixelWrapper(pixelOptions);
        }
    }
}
