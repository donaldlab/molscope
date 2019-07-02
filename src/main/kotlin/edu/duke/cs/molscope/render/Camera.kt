package edu.duke.cs.molscope.render

import cuchaz.kludge.tools.*
import cuchaz.kludge.vulkan.*
import org.joml.*
import java.lang.Float.min
import java.nio.ByteBuffer


class Camera internal constructor(
	internal val device: Device
) : AutoCloseable {

	private val closer = AutoCloser()
	private fun <R:AutoCloseable> R.autoClose() = also { closer.add(this@autoClose) }
	override fun close() = closer.close()

	val pos: Vector3f = Vector3f(0f, 0f, -2f)
	val lookAt: Vector3f = Vector3f(0f, 0f, 0f)
	val up: Vector3f = Vector3f(0f, 1f, 0f)
	val size: Vector2f = Vector2f(320f, 160f)
	var zNear: Float = 1f
	var zFar: Float = 3f
	var magnification: Float = 40f

	val side: Vector3f = Vector3f()
	val look: Vector3f = Vector3f()

	var viewDistance: Float
		get() = zFar - zNear
		set(value) {
			zFar = zNear + value
		}

	/** side = up x look */
	fun updateSide() = side.set(up).cross(look)

	/** look = norm(lookAt - pos) */
	fun updateLook() = look.set(lookAt).sub(pos).normalize()

	init {
		updateSide()
		updateLook()
	}

	val buf = device
		.buffer(
			size = 21*Float.SIZE_BYTES.toLong(),
			usage = IntFlags.of(Buffer.Usage.UniformBuffer, Buffer.Usage.TransferDst)
		)
		.autoClose()
		.allocateDevice()
		.autoClose()

	fun set(other: Camera) {
		this.pos.set(other.pos)
		this.lookAt.set(other.lookAt)
		this.up.set(other.up)
		this.size.set(other.size)
		this.zNear = other.zNear
		this.zFar = other.zFar
		this.magnification = other.magnification

		this.side.set(other.side)
		this.look.set(other.look)
	}

	fun resize(width: Int, height: Int) {
		size.set(width.toFloat(), height.toFloat())
	}

	fun upload() {
		buf.transferHtoD { buf ->

			fun ByteBuffer.put(v: Vector3f) {
				putFloat(v.x)
				putFloat(v.y)
				putFloat(v.z)
				putFloat(Float.NaN) // 4 bytes pad
			}

			fun ByteBuffer.put(v: Vector2f) {
				putFloat(v.x)
				putFloat(v.y)
			}

			buf.put(pos)
			buf.put(side)
			buf.put(up)
			buf.put(look)
			buf.put(size)
			buf.putFloat(zNear)
			buf.putFloat(zFar)
			buf.putFloat(magnification)
			buf.flip()
		}
	}

	fun lookAtBox(width: Int, height: Int, focalLength: Float, look: Vector3f, up: Vector3f, box: AABBf) {

		// set camera orientation
		this.look.set(look).normalize()
		this.up.set(up).normalize()
		updateSide()

		// copy window size
		size.set(width.toFloat(), height.toFloat())

		// look at the center of the box
		lookAt.boxCenter(box)

		// get the nearest and farthest corners of the box
		val cornersDist = (0 until box.numCorners)
			.map { i -> Vector3f().boxCorner(box, i) }
			.map { p -> p to Vector3f(p).sub(lookAt).parallelTo(this.look).dot(this.look) }
		val (nearCorner, near) = cornersDist.minBy { (_, dot) -> dot }!!
		val (farCorner, far) = cornersDist.maxBy { (_, dot) -> dot }!!

		pos.set(this.look)
			.mul(near - focalLength)
			.add(lookAt)
		zNear = focalLength
		zFar = focalLength + far - near

		// calc the smallest magnification that shows the whole box
		val center = Vector3f().boxCenter(box)
		magnification = min(
			min(
				width/(box.maxX - center.x)/2,
				width/(center.x - box.minX)/2
			),
			min(
				height/(box.maxY - center.y)/2,
				height/(center.y - box.minY)/2
			)
		)
	}

	fun rotate(q: Quaternionf) {

		// rotate the position about the look point
		pos
			.sub(lookAt)
			.rotate(q)
			.add(lookAt)

		// rotate the orientation too
		side.rotate(q)
		up.rotate(q)
		look.rotate(q)
	}

	fun lookAt(target: Vector3fc) {

		pos.add(target).sub(lookAt)
		lookAt.set(target)
	}

	inner class Rotator {

		val camera = this@Camera

		// copy the internal state
		val pos = Vector3f()
		val side = Vector3f()
		val up = Vector3f()
		val look = Vector3f()

		val q = Quaternionf()

		init {
			capture()
		}

		fun capture() {

			// copy the internal state
			pos.set(camera.pos)
			side.set(camera.side)
			up.set(camera.up)
			look.set(camera.look)
		}

		fun update() {

			// restore the internal state
			camera.pos.set(pos)
			camera.side.set(side)
			camera.up.set(up)
			camera.look.set(look)

			// then apply the rotation
			camera.rotate(q)
		}
	}
}

fun Vector3f.worldToCamera(camera: Camera) = this
	.sub(camera.pos)
	.set(
		camera.side.dot(this),
		camera.up.dot(this),
		camera.look.dot(this)
	)
