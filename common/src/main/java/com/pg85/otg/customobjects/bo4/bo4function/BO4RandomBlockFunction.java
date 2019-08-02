package com.pg85.otg.customobjects.bo4.bo4function;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import com.pg85.otg.common.LocalMaterialData;
import com.pg85.otg.common.LocalWorld;
import com.pg85.otg.customobjects.bo3.BO3Loader;
import com.pg85.otg.customobjects.bo4.BO4Config;
import com.pg85.otg.customobjects.structures.bo4.BO4CustomStructureCoordinate;
import com.pg85.otg.exception.InvalidConfigException;
import com.pg85.otg.util.bo3.NamedBinaryTag;
import com.pg85.otg.util.bo3.Rotation;
import com.pg85.otg.util.helpers.MaterialHelper;
import com.pg85.otg.util.helpers.StreamHelper;

public class BO4RandomBlockFunction extends BO4BlockFunction
{
    public LocalMaterialData[] blocks;
    public byte[] blockChances;
    public String[] metaDataNames;
    public NamedBinaryTag[] metaDataTags;

    public byte blockCount = 0;   

    public BO4RandomBlockFunction() { }
    
    public BO4RandomBlockFunction(BO4Config holder)
    {
    	super(holder);
    }
    
    @Override
    public void load(List<String> args) throws InvalidConfigException
    {
        assureSize(5, args);
        x = readInt(args.get(0), -100, 100);
        y = (short) readInt(args.get(1), -1000, 1000);
        z = readInt(args.get(2), -100, 100);

        // Now read the random parts
        int i = 3;
        int size = args.size();

        // Get number of blocks first, params can vary so can't just count.
        while (i < size)
        {           
        	i++;
            if (i >= size)
            {
                throw new InvalidConfigException("Missing chance parameter");
            }
            try
            {
                readInt(args.get(i), 1, 100);
            }
            catch (InvalidConfigException e)
            {
                // Get the chance
                i++;
                if (i >= size)
                {
                    throw new InvalidConfigException("Missing chance parameter");
                }
                readInt(args.get(i), 1, 100);
            }
            i++;
            blockCount++;
        }
        
        this.blocks = new LocalMaterialData[blockCount];
        this.blockChances = new byte[blockCount];
        this.metaDataNames = new String[blockCount];
        this.metaDataTags = new NamedBinaryTag[blockCount];
        
        i = 3;
        blockCount = 0;
        while (i < size)
        {
            // Parse chance and metadata
        	this.blocks[blockCount] = MaterialHelper.readMaterial(args.get(i));
            i++;
            if (i >= size)
            {
                throw new InvalidConfigException("Missing chance parameter");
            }
            try
            {
                blockChances[blockCount] = (byte) readInt(args.get(i), 1, 100);
            }
            catch (InvalidConfigException e)
            {
                // Maybe it's a NBT file?

                // Get the file
                NamedBinaryTag metaData = BO3Loader.loadMetadata(args.get(i), this.getHolder().getFile());
                if (metaData != null)
                {
                    metaDataNames[blockCount] = args.get(i);
                    metaDataTags[blockCount] = metaData;
                }

                // Get the chance
                i++;
                if (i >= size)
                {
                    throw new InvalidConfigException("Missing chance parameter");
                }
                blockChances[blockCount] = (byte) readInt(args.get(i), 1, 100);
            }

            i++;
            blockCount++;
        }
    }
    
    public BO4RandomBlockFunction rotate(Rotation rotation)
    {
    	BO4RandomBlockFunction rotatedBlock = new BO4RandomBlockFunction(this.getHolder());

        BO4CustomStructureCoordinate rotatedCoords = BO4CustomStructureCoordinate.getRotatedBO3CoordsJustified(x, y, z, rotation);

        rotatedBlock.x = rotatedCoords.getX();
        rotatedBlock.y = rotatedCoords.getY();
        rotatedBlock.z = rotatedCoords.getZ();

        rotatedBlock.blocks = blocks;
        
    	// TODO: This makes no sense, why is rotation inverted??? Should be: NORTH:0,WEST:1,SOUTH:2,EAST:3
        LocalMaterialData[] rotatedBlockBlocks = new LocalMaterialData[blockCount];
        for (int a = 0; a < blockCount; a++)
        {
        	rotatedBlockBlocks[a] = rotatedBlock.blocks[a];

		    // Apply rotation
			if(rotation.getRotationId() == 3)
			{
				rotatedBlockBlocks[a] = rotatedBlockBlocks[a].rotate(1);
			}
			if(rotation.getRotationId() == 2)
			{
				rotatedBlockBlocks[a] = rotatedBlockBlocks[a].rotate(2);
			}
			if(rotation.getRotationId() == 1)
			{
				rotatedBlockBlocks[a] = rotatedBlockBlocks[a].rotate(3);
			}
        }
        rotatedBlock.blocks = rotatedBlockBlocks;

    	rotatedBlock.blockCount = blockCount;
    	rotatedBlock.blockChances = blockChances;
        rotatedBlock.metaDataTag = metaDataTag;
        rotatedBlock.metaDataTags = metaDataTags;
        rotatedBlock.metaDataName = metaDataName;
        rotatedBlock.metaDataNames = metaDataNames;

        return rotatedBlock;
    }

    @Override
    public void spawn(LocalWorld world, Random random, int x, int y, int z, boolean allowOutsidePopulatingArea)
    {
        for (int i = 0; i < blockCount; i++)
        {
            if (random.nextInt(100) < blockChances[i])
            {
                world.setBlock(x, y, z, blocks[i], metaDataTags[i], allowOutsidePopulatingArea);
                break;
            }
        }
    }
    
    @Override
    public String makeString()
    {
        String text = "RandomBlock(" + x + "," + y + "," + z;
        for (int i = 0; i < blockCount; i++)
        {
            if (metaDataTags[i] == null)
            {
                text += "," + blocks[i] + "," + blockChances[i];
            } else
            {
                text += "," + blocks[i] + "," + metaDataNames[i] + "," + blockChances[i];
            }
        }
        return text + ")";
    }
    
    @Override
    public Class<BO4Config> getHolderType()
    {
        return BO4Config.class;
    }
    
    @Override
    public void writeToStream(String[] metaDataNames, LocalMaterialData[] materials, DataOutput stream) throws IOException
    {
        stream.writeShort(this.y);
        
        stream.writeByte(this.blocks.length);
        boolean bFound;
        for(int i = 0; i < this.blocks.length; i++)
        {
        	byte blockChance = this.blockChances[i];
        	stream.writeByte(blockChance);
        	
        	bFound = false;
        	if(this.blocks[i] != null)
        	{
		        for(int j = 0; j < materials.length; j++)
		        {
		        	if(materials[j] == this.blocks[i])
		        	{
		        		stream.writeShort(j);
		        		bFound = true;
		        		break;
		        	}
		        }
        	}
	        if(!bFound)
	        {
	        	stream.writeShort(-1);
	        }
	    }
        
        boolean metaDataFound = false;
        for(int i = 0; i < this.metaDataNames.length; i++)
        {
        	if(this.metaDataNames[i] != null)
        	{
        		metaDataFound = true;
        		break;
        	}
        }
        
        if(metaDataFound)
        {
        	stream.writeByte(this.blocks.length);
	        for(int i = 0; i < this.metaDataNames.length; i++)
	        {
	        	bFound = false;
	        	if(this.metaDataNames[i] != null)
	        	{
		            for(int j = 0; j < metaDataNames.length; j++)
		            {
		            	if(metaDataNames[j].equals(this.metaDataNames[i]))
		            	{
		            		stream.writeShort(i);
		            		bFound = true;
		            		break;
		            	}
		            }
	        	}
		        if(!bFound)
		        {
		        	stream.writeShort(-1);
		        }
	        }
        } else {
        	stream.writeByte(0);
        }
    }
    
    public static BO4RandomBlockFunction fromStream(BO4Config holder, DataInputStream stream) throws IOException
    {
    	BO4RandomBlockFunction rbf = new BO4RandomBlockFunction(holder);
    	
    	File file = holder.getFile();
    	
    	rbf.x = stream.readByte();
    	rbf.y = stream.readShort();
    	rbf.z = stream.readByte();
    	
    	rbf.blockCount = stream.readByte();
    	
    	rbf.blocks = new LocalMaterialData[rbf.blockCount];
    	rbf.blockChances = new byte[rbf.blockCount];
    	rbf.metaDataNames = new String[rbf.blockCount];
    	rbf.metaDataTags = new NamedBinaryTag[rbf.blockCount];

    	int blocksLength = stream.readByte();
    	for(int i = 0; i < blocksLength; i++)
    	{
    		try {
				rbf.blocks[i] = MaterialHelper.readMaterial(StreamHelper.readStringFromStream(stream));
			}
    		catch (InvalidConfigException e) { }
    		rbf.blockChances[i] = stream.readByte();
    		rbf.metaDataNames[i] = StreamHelper.readStringFromStream(stream);
    		if(rbf.metaDataNames[i] != null)
    		{
	            // Get the file
	            NamedBinaryTag metaData = BO3Loader.loadMetadata(rbf.metaDataNames[i], file);
	       	   	
	            if (metaData != null)
	            {
	            	rbf.metaDataTags[i] = metaData;
	            } else {
	            	rbf.metaDataNames[i] = null;
	            }
    		}
    	}
    	
    	return rbf;
    }
}