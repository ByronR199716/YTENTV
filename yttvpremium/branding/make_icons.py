from PIL import Image, ImageDraw, ImageFont
import os
S=4
def logo(size, bg=None):
    W=size*S
    im=Image.new('RGBA',(W,W),bg or (0,0,0,0)); d=ImageDraw.Draw(im)
    k=W/1024
    d.ellipse([(512-480)*k,(512-480)*k,(512+480)*k,(512+480)*k],fill=(0xd9,0x1e,0x2e,255))
    # play triangle (approx of rounded path)
    d.polygon([(250*k,330*k),(250*k,694*k),(552*k,512*k)],fill='white')
    for x,y,h in [(600,430,164),(672,350,324),(744,400,224)]:
        d.rounded_rectangle([x*k,y*k,(x+44)*k,(y+h)*k],radius=22*k,fill='white')
    return im.resize((size,size),Image.LANCZOS)
out={}
for name,sz in [('mipmap-mdpi',80),('mipmap-hdpi',120),('mipmap-xhdpi',160),('mipmap-xxhdpi',240),('mipmap-xxxhdpi',320)]:
    os.makedirs(name,exist_ok=True); logo(sz).save(f'{name}/ic_app.png')
font='/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf'
for name,(w,h) in [('drawable',(160,90)),('drawable-hdpi',(240,135)),('drawable-xhdpi',(320,180)),('drawable-xxhdpi',(480,270)),('drawable-xxxhdpi',(640,360))]:
    W,H=w*S,h*S
    im=Image.new('RGBA',(W,H),(15,15,18,255)); d=ImageDraw.Draw(im)
    ls=int(H*0.46); lg=logo(ls).resize((ls,ls))
    f=ImageFont.truetype(font,int(H*0.15))
    tw=d.textlength('YTTVPremium',font=f)
    total=ls+int(H*0.06)+tw
    x0=int((W-total)/2)
    im.alpha_composite(lg,(x0,int((H-ls)/2)))
    d.text((x0+ls+int(H*0.06),H/2),'YTTVPremium',font=f,fill='white',anchor='lm')
    os.makedirs(name,exist_ok=True); im.resize((w,h),Image.LANCZOS).save(f'{name}/app_banner.png')
logo(512).save('logo-512.png')
